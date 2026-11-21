/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Choreographer;
import android.view.ViewStub;
import android.widget.TextView;
import android.widget.NumberPicker;
import android.widget.Toast;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.util.LocaleUtils;

import com.google.android.setupcompat.util.SystemBarHelper;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.OneShotPreDrawListener;

import org.lineageos.setupwizard.widget.LocalePicker;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocaleActivity extends DateTimePageHost {

    private static final String TAG = LocaleActivity.class.getSimpleName();

    private List<com.android.internal.app.LocalePicker.LocaleInfo> mLocales;
    private Locale mCurrentLocale;
    private LocalePicker mLanguagePicker;
    private ExecutorService mFetchUpdateSimLocaleTask;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mPendingLocaleUpdate;
    private boolean mPaused = true;
    private Intent mDateStep;
    private View mDateContent;
    private final OnBackPressedCallback mDateBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            displayStep(null);
        }
    };

    private final Runnable mUpdateLocale = new Runnable() {
        public void run() {
            if (mCurrentLocale != null) {
                com.android.internal.app.LocalePicker.updateLocale(mCurrentLocale);
            }
        }
    };

    private final BroadcastReceiver mSimChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                fetchAndUpdateSimLocale();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarHelper.setBackButtonVisible(getWindow(), true);
        setNextText(R.string.next);
        mLanguagePicker = findViewById(R.id.locale_list);
        mLanguagePicker.setNextRight(getNextButton().getId());
        mLanguagePicker.requestFocus();
        if (getResources().getBoolean(R.bool.config_isLargeNoTouch)) {
            mLanguagePicker.setOnClickListener((View v) -> getNextButton().performClick());
        }
        loadLanguages();
        getOnBackPressedDispatcher().addCallback(this, mDateBack);
        View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnDrawListener(() -> {
            if (Trace.isTagEnabled(Trace.TRACE_TAG_VIEW)) {
                String page = mDateStep == null ? "Language" : "Date";
                Trace.traceBegin(Trace.TRACE_TAG_VIEW,
                        "WizardPage:" + page + ":" + Choreographer.getInstance().getVsyncId());
                Trace.traceEnd(Trace.TRACE_TAG_VIEW);
            }
        });
        Intent saved = savedInstanceState == null ? null
                : savedInstanceState.getParcelable("dateStep", Intent.class);
        if (saved != null) {
            displayStep(saved);
        } else {
            OneShotPreDrawListener.add(decor, () -> decor.post(() -> {
                if (!isDestroyed() && !isFinishing() && mDateStep == null) {
                    prepareDateContent();
                }
            }));
        }
    }

    private void prepareDateContent() {
        if (mDateContent == null) {
            mDateContent = ((ViewStub) findViewById(R.id.date_stub)).inflate();
            mDateContent.setVisibility(View.GONE);
        }
        bindDateControls(mDateContent);
    }

    private void displayStep(Intent date) {
        mDateStep = date;
        boolean showDate = date != null;
        if (showDate) {
            prepareDateContent();
            if (!mPaused) startDateControls();
        } else {
            stopDateControls();
        }
        findViewById(R.id.language_content).setVisibility(showDate ? View.GONE : View.VISIBLE);
        mDateContent.setVisibility(showDate ? View.VISIBLE : View.GONE);
        updatePageHeader();
        updateDescription();
        mDateBack.setEnabled(showDate);
        if (!showDate) mLanguagePicker.requestFocus();
    }

    private void updateDescription() {
        boolean date = mDateStep != null;
        getGlifLayout().setDescriptionText(date ? getText(R.string.date_time_summary) : null);
        TextView description = getGlifLayout().getDescriptionTextView();
        if (description != null) description.setVisibility(date ? View.VISIBLE : View.GONE);
    }

    @Override
    protected Intent currentStepIntent() {
        return mDateStep == null ? super.currentStepIntent() : mDateStep;
    }

    @Override
    protected boolean showLocalStep(Intent intent) {
        if (mDateStep != null || !new ComponentName(this, DateTimeActivity.class).equals(
                intent.resolveActivity(getPackageManager()))) {
            return false;
        }
        displayStep(intent);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable("dateStep", mDateStep);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        WizardPageData.zones(this);
        // Keep the navigation button and its input window alive during translation.
        getGlifLayout().setHeaderText(getText(getTitleResId()));
        setNextText(R.string.next);
        getWindow().getDecorView().setLayoutDirection(config.getLayoutDirection());
        if (mDateStep != null) {
            prepareDateContent();
            updateDescription();
            updateTimeAndDateDisplay();
        }
    }

    @Override
    protected void onNextPressed() {
        // A quick tap can precede the debounced system-wide language change.
        if (mHandler.hasCallbacks(mUpdateLocale)) {
            mHandler.removeCallbacks(mUpdateLocale);
            mUpdateLocale.run();
        }
        super.onNextPressed();
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mUpdateLocale);
        super.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        mPaused = true;
        unregisterReceiver(mSimChangedReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPaused = false;
        if (mDateStep != null) startDateControls();
        registerReceiver(mSimChangedReceiver,
                new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        if (mLanguagePicker != null) {
            mLanguagePicker.setEnabled(true);
        }
        if (mPendingLocaleUpdate) {
            mPendingLocaleUpdate = false;
            fetchAndUpdateSimLocale();
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_locale;
    }

    @Override
    protected int getTitleResId() {
        return mDateStep == null ? R.string.setup_locale : R.string.setup_datetime;
    }

    @Override
    protected int getIconResId() {
        return mDateStep == null ? R.drawable.ic_locale : R.drawable.ic_datetime;
    }

    private void loadLanguages() {
        mLocales = WizardPageData.ready(WizardPageData.languages(this));
        WizardPageData.zones(this);
        mCurrentLocale = Locale.getDefault();
        fetchAndUpdateSimLocale();
        int currentLocaleIndex = 0;
        String[] labels = new String[mLocales.size()];
        for (int i = 0; i < labels.length; i++) {
            com.android.internal.app.LocalePicker.LocaleInfo localLocaleInfo =
                    mLocales.get(i);
            Locale localLocale = localLocaleInfo.getLocale();
            if (localLocale.equals(mCurrentLocale)) {
                currentLocaleIndex = i;
            }
            labels[i] = localLocaleInfo.getLabel();
        }
        mLanguagePicker.setDisplayedValues(labels);
        mLanguagePicker.setMaxValue(labels.length - 1);
        mLanguagePicker.setValue(currentLocaleIndex);
        mLanguagePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        mLanguagePicker.setOnValueChangedListener((pkr, oldVal, newVal) -> setLocaleFromPicker());

        mLanguagePicker.setOnScrollListener((view, scrollState) -> {
            if (scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                ((SetupWizardApp) getApplication()).setIgnoreSimLocale(true);
            }
        });
    }

    private void setLocaleFromPicker() {
        ((SetupWizardApp) getApplication()).setIgnoreSimLocale(true);
        int i = mLanguagePicker.getValue();
        final com.android.internal.app.LocalePicker.LocaleInfo localLocaleInfo =
                mLocales.get(i);
        onLocaleChanged(localLocaleInfo.getLocale());
    }

    private void onLocaleChanged(Locale paramLocale) {
        mLanguagePicker.setEnabled(true);
        mHandler.removeCallbacks(mUpdateLocale);
        mCurrentLocale = paramLocale;
        Configuration next = new Configuration(getResources().getConfiguration());
        next.setLocale(paramLocale);
        WizardPageData.zones(createConfigurationContext(next));
        mHandler.postDelayed(mUpdateLocale, 1000);
    }

    private void fetchAndUpdateSimLocale() {
        if (((SetupWizardApp) getApplication()).ignoreSimLocale() || isDestroyed()) {
            return;
        }
        if (mPaused) {
            mPendingLocaleUpdate = true;
            return;
        }
        if (mFetchUpdateSimLocaleTask != null) {
            mFetchUpdateSimLocaleTask.shutdown();
        }
        mFetchUpdateSimLocaleTask = Executors.newSingleThreadExecutor();
        mFetchUpdateSimLocaleTask.execute(() -> {
            Locale locale = null;
            Activity activity = LocaleActivity.this;
            if (!activity.isFinishing() || !activity.isDestroyed()) {
                // If the sim is currently pin locked, return
                TelephonyManager telephonyManager = (TelephonyManager)
                        activity.getSystemService(Context.TELEPHONY_SERVICE);
                int state = telephonyManager.getSimState();
                if (state == TelephonyManager.SIM_STATE_PIN_REQUIRED ||
                        state == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
                    return;
                }

                final SubscriptionManager subscriptionManager =
                        activity.getSystemService(SubscriptionManager.class);
                List<SubscriptionInfo> activeSubs =
                        subscriptionManager.getActiveSubscriptionInfoList();
                if (activeSubs == null || activeSubs.isEmpty()) {
                    return;
                }

                // Fetch locale for active sim's MCC
                final String mccString = activeSubs.get(0).getMccString();
                try {
                    if (mccString != null && !mccString.isEmpty()) {
                        int mcc = Integer.parseInt(mccString);
                        locale = LocaleUtils.getLocaleFromMcc(activity, mcc, null);
                    } else {
                        Log.w(TAG, "Unexpected mccString: '" + mccString + "'");
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "mccString not a number: '" + mccString + "'", e);
                }

                // If that fails, fall back to preferred languages reported
                // by the sim
                if (locale == null) {
                    Locale simLocale = telephonyManager.getSimLocale();
                    if (simLocale != null) {
                        locale = simLocale;
                    }
                }
                Locale finalLocale = locale;
                mHandler.post(() -> {
                    if (finalLocale != null && !finalLocale.equals(mCurrentLocale)) {
                        if (!((SetupWizardApp) getApplication()).ignoreSimLocale()
                                && !isDestroyed()) {
                            String label = getString(R.string.sim_locale_changed,
                                    finalLocale.getDisplayName());
                            Toast.makeText(LocaleActivity.this, label, Toast.LENGTH_SHORT).show();
                            onLocaleChanged(finalLocale);
                            ((SetupWizardApp) getApplication()).setIgnoreSimLocale(true);
                        }
                    }
                });
            }
        });
    }
}
