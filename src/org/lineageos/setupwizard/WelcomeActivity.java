/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static org.lineageos.setupwizard.SetupWizardApp.ACTION_EMERGENCY_DIAL;

import android.content.Intent;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.view.OneShotPreDrawListener;

import com.google.android.setupcompat.util.WizardManagerHelper;

import com.google.android.setupcompat.template.FooterButtonStyleUtils;
import com.google.android.setupcompat.util.SystemBarHelper;

import org.lineageos.setupwizard.util.SetupWizardUtils;
import org.lineageos.setupwizard.wizardmanager.WizardManager;

public class WelcomeActivity extends LocaleActivity {

    private static final String ACTION_ACCESSIBILITY_SETTINGS =
            "android.settings.ACCESSIBILITY_SETTINGS_FOR_SUW";

    private View mWelcome;
    private View mPages;
    private static boolean sPreparedLanguage;
    private Intent mLanguageStep;
    private Intent mFirstStep;
    private ActivityResultLauncher<Intent> mAuxiliary;
    private final OnBackPressedCallback mWelcomeBack = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (showingDate()) {
                displayStep(null);
            } else if (mLanguageStep != null) {
                showWelcome(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onSetupStart();
        mAuxiliary = registerForActivityResult(new StartDecoratedActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_CANCELED) {
                nextAction(result.getResultCode(), result.getData());
            }
        });
        mPages = getGlifLayout();
        FrameLayout content = findViewById(android.R.id.content);
        mWelcome = getLayoutInflater().inflate(R.layout.welcome_activity, content, false);
        content.addView(mWelcome);
        bindWelcome();
        mLanguageStep = savedInstanceState == null ? null
                : savedInstanceState.getParcelable("languageStep", Intent.class);
        getOnBackPressedDispatcher().addCallback(this, mWelcomeBack);
        showWelcome(mLanguageStep == null);
    }

    private void launchAuxiliary(String action) {
        Intent intent = new Intent(action);
        Bundle state = getIntent().getBundleExtra(SetupWizardApp.EXTRA_WIZARD_BUNDLE);
        if (state != null) {
            Bundle copy = new Bundle(state);
            copy.putFloat(WizardManagerHelper.EXTRA_SETUP_WINDOW_BRIGHTNESS,
                    getWindow().getAttributes().screenBrightness);
            intent.putExtra(SetupWizardApp.EXTRA_WIZARD_BUNDLE, copy);
        }
        mAuxiliary.launch(intent);
    }

    private void prepareLanguageDrawing() {
        if (!"v500".equals(android.os.Build.DEVICE) || sPreparedLanguage || mLanguageStep != null
                || mPages.getWidth() == 0 || mPages.getHeight() == 0
                || !mPages.isHardwareAccelerated() || isFinishing() || isDestroyed()) return;
        sPreparedLanguage = true;
        // Record the real picker with its current font, theme and footer. The
        // reduced offscreen buffer lets HWUI compile the same drawing programs
        // on first run without creating another full-size tablet buffer.
        final int width = mPages.getWidth();
        final int height = mPages.getHeight();
        final int outWidth = Math.min(width, 360);
        final int outHeight = Math.max(1, height * outWidth / width);
        RenderNode node = new RenderNode("Setup language preparation");
        node.setPosition(0, 0, outWidth, outHeight);
        RecordingCanvas canvas = node.beginRecording(outWidth, outHeight);
        try {
            canvas.scale((float) outWidth / width, (float) outHeight / height);
            mPages.draw(canvas);
            Button start = mWelcome.findViewById(R.id.start);
            Drawable.ConstantState state = start.getBackground().getConstantState();
            if (state != null) {
                Drawable pressed = state.newDrawable(getResources(), getTheme()).mutate();
                pressed.setBounds(0, 0, start.getWidth(), start.getHeight());
                pressed.setHotspot(start.getWidth() / 2f, start.getHeight() / 2f);
                pressed.setState(new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed});
                pressed.jumpToCurrentState();
                pressed.draw(canvas);
            }
            Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
            ink.setColor(0x66909090);
            canvas.drawCircle(42, 42, 30, ink);
        } finally {
            node.endRecording();
        }
        new Thread(() -> {
            try {
                Bitmap bitmap = HardwareRenderer.createHardwareBitmap(node, outWidth, outHeight);
                if (bitmap != null) bitmap.recycle();
            } catch (RuntimeException e) {
                android.util.Log.w("SetupWizard", "Offscreen language rendering unavailable", e);
            } finally {
                node.discardDisplayList();
            }
        }, "Setup language drawing").start();
    }

    private void bindWelcome() {
        Button startButton = mWelcome.findViewById(R.id.start);
        Button emergButton = mWelcome.findViewById(R.id.emerg_dialer);
        startButton.setOnClickListener(view -> {
            if (mFirstStep == null) {
                nextAction(RESULT_OK);
                return;
            }
            setResult(RESULT_OK);
            mLanguageStep = new Intent(mFirstStep);
            showWelcome(false);
        });
        mWelcome.findViewById(R.id.launch_accessibility)
                .setOnClickListener(
                        view -> launchAuxiliary(ACTION_ACCESSIBILITY_SETTINGS));

        FooterButtonStyleUtils.applyPrimaryButtonPartnerResource(this, startButton, true);

        if (SetupWizardUtils.hasTelephony(this)) {
            emergButton.setOnClickListener(
                    view -> launchAuxiliary(ACTION_EMERGENCY_DIAL));

            FooterButtonStyleUtils.applySecondaryButtonPartnerResource(this, emergButton, true);
        } else {
            emergButton.setVisibility(View.GONE);
        }

        TextView welcomeTitle = mWelcome.findViewById(R.id.welcome_title);
        if (SetupWizardUtils.isManagedProfile(this)) {
            welcomeTitle.setText(getString(R.string.setup_managed_profile_welcome_message));
        } else {
            welcomeTitle.setText(getString(R.string.setup_welcome_message,
                    getString(R.string.os_name)));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable("languageStep", mLanguageStep);
    }

    @Override
    protected String drawnPage() {
        return mLanguageStep == null ? "Welcome" : super.drawnPage();
    }

    @Override
    protected Intent currentStepIntent() {
        if (mLanguageStep != null && !showingDate()) return mLanguageStep;
        return super.currentStepIntent();
    }

    @Override
    protected boolean showLocalStep(Intent intent) {
        if (mLanguageStep == null && new ComponentName(this, LocaleActivity.class)
                .equals(intent.resolveActivity(getPackageManager()))) {
            mLanguageStep = intent;
            showWelcome(false);
            return true;
        }
        return super.showLocalStep(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        ViewGroup parent = (ViewGroup) mWelcome.getParent();
        parent.removeView(mWelcome);
        mWelcome = getLayoutInflater().inflate(R.layout.welcome_activity, parent, false);
        parent.addView(mWelcome);
        bindWelcome();
        if (hasLocalPage()) {
            mWelcome.setVisibility(View.INVISIBLE);
        } else {
            showWelcome(mLanguageStep == null);
        }
    }

    private void showWelcome(boolean welcome) {
        if (welcome) mLanguageStep = null;
        mWelcome.setVisibility(welcome ? View.VISIBLE : View.INVISIBLE);
        mPages.setVisibility(welcome ? View.INVISIBLE : View.VISIBLE);
        if (welcome) {
            SystemBarHelper.setBackButtonVisible(getWindow(), false);
            OneShotPreDrawListener.add(mWelcome, () -> mWelcome.post(() -> {
                if (isFinishing() || isDestroyed() || mLanguageStep != null) return;
                Intent next = WizardManager.resolveLocalPage(this,
                        WizardManagerHelper.getNextIntent(getIntent(), RESULT_OK, null));
                if (new ComponentName(this, LocaleActivity.class).equals(next.getComponent())) {
                    mFirstStep = next;
                    prepareLanguageDrawing();
                }
            }));
        } else {
            OneShotPreDrawListener.add(mPages, () -> mPages.postOnAnimation(() -> {
                if (!isDestroyed() && mLanguageStep != null) {
                    SystemBarHelper.setBackButtonVisible(getWindow(), true);
                }
            }));
        }
        if (!welcome && !showingDate() && !hasLocalPage() && !mPages.isInTouchMode()) {
            mPages.findViewById(R.id.locale_list).requestFocus();
        }
    }
}
