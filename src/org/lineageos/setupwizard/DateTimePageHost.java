/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;


import org.lineageos.setupwizard.util.SetupWizardUtils;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Date and time controls used by the standalone step and the local two-page flow. */
abstract class DateTimePageHost extends BaseSetupWizardActivity implements
        TimePickerDialog.OnTimeSetListener, DatePickerDialog.OnDateSetListener {

    private static final String KEY_ID = "id"; // value: String
    private static final String KEY_DISPLAYNAME = "name"; // value: String
    private static final String KEY_GMT = "gmt"; // value: String

    private TimeZone mCurrentTimeZone;
    private TextView mDateTextView;
    private TextView mTimeTextView;

    private boolean mWatchingTime;
    private java.util.concurrent.CompletableFuture<List<Map<String, Object>>> mZoneRequest;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTimeAndDateDisplay();
        }
    };

    protected final void bindDateControls(View root) {
        var request = WizardPageData.zones(this);
        if (request == mZoneRequest) return;
        mZoneRequest = request;
        ((TextView) root.findViewById(R.id.date_title)).setText(R.string.setup_current_date);
        ((TextView) root.findViewById(R.id.time_title)).setText(R.string.setup_current_time);
        final Spinner spinner = root.findViewById(R.id.timezone_list);
        spinner.setEnabled(false);
        View dateView = root.findViewById(R.id.date_item);
        dateView.setOnClickListener((view) -> showDatePicker());
        View timeView = root.findViewById(R.id.time_item);
        timeView.setOnClickListener((view) -> showTimePicker());
        mDateTextView = root.findViewById(R.id.date_text);
        mTimeTextView = root.findViewById(R.id.time_text);
        request.thenAcceptAsync(zones -> {
            if (request != mZoneRequest || isFinishing() || isDestroyed()) return;
            bindZones(spinner, zones);
        }, getMainExecutor());
    }

    private void bindZones(Spinner spinner, List<Map<String, Object>> zones) {
        SimpleAdapter adapter = constructTimezoneAdapter(this, zones);
        mCurrentTimeZone = TimeZone.getDefault();
        spinner.setEnabled(true);
        // Pre-select current/default timezone
        int tzIndex = getTimeZoneIndex(adapter, mCurrentTimeZone);
        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(adapter);
        if (tzIndex != -1) {
            spinner.setSelection(tzIndex);
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position,
                    long id) {
                final Map<?, ?> map = (Map<?, ?>) adapterView.getItemAtPosition(position);
                final String tzId = (String) map.get(KEY_ID);
                if (mWatchingTime && mCurrentTimeZone != null && !mCurrentTimeZone.getID().equals(tzId)) {
                    // Update the system timezone value
                    final AlarmManager alarm =
                            (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    alarm.setTimeZone(tzId);
                    mCurrentTimeZone = TimeZone.getTimeZone(tzId);
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    protected final void startDateControls() {
        if (mWatchingTime) return;
        mWatchingTime = true;
        final Calendar calendar = Calendar.getInstance();
        final boolean isEpoch = calendar.get(Calendar.YEAR) == 1970;
        if (isEpoch) {
            // If epoch, set date to build date
            long timestamp = SetupWizardUtils.getBuildDateTimestamp();
            if (timestamp > 0) {
                calendar.setTimeInMillis(timestamp * 1000);
                setDate(this, calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            } else {
                // no build date available, use a sane default
                setDate(this, 2017, Calendar.JANUARY, 1);
            }
        }
        // Register for time ticks and other reasons for time change
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mIntentReceiver, filter, null, null);

        updateTimeAndDateDisplay();
    }

    protected final void stopDateControls() {
        if (mWatchingTime) {
            unregisterReceiver(mIntentReceiver);
            mWatchingTime = false;
        }
    }

    @Override
    public void onPause() {
        stopDateControls();
        super.onPause();
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int day) {
        setDate(this, year, month, day);
        updateTimeAndDateDisplay();
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        setTime(this, hourOfDay, minute);
        updateTimeAndDateDisplay();
    }

    private void showDatePicker() {
        DateTimeActivity.DatePickerFragment datePickerFragment = DateTimeActivity.DatePickerFragment.newInstance();
        datePickerFragment.show(getSupportFragmentManager(), DateTimeActivity.DatePickerFragment.TAG);
    }

    private void showTimePicker() {
        DateTimeActivity.TimePickerFragment timePickerFragment = DateTimeActivity.TimePickerFragment.newInstance();
        timePickerFragment.show(getSupportFragmentManager(), DateTimeActivity.TimePickerFragment.TAG);
    }

    protected final void updateTimeAndDateDisplay() {
        java.text.DateFormat shortDateFormat = DateFormat.getDateFormat(this);
        final Calendar now = Calendar.getInstance();
        mTimeTextView.setText(DateFormat.getTimeFormat(this).format(now.getTime()));
        mDateTextView.setText(shortDateFormat.format(now.getTime()));
    }

    private static SimpleAdapter constructTimezoneAdapter(Context context, List<Map<String, Object>> sortedList) {
        final String[] from = new String[]{KEY_DISPLAYNAME, KEY_GMT};
        final int[] to = new int[]{android.R.id.text1, android.R.id.text2};

        return new SimpleAdapter(context,
                sortedList,
                R.layout.date_time_setup_custom_list_item_2,
                from,
                to);
    }

    private static int getTimeZoneIndex(SimpleAdapter adapter, TimeZone tz) {
        final String defaultId = tz.getID();
        final int listSize = adapter.getCount();
        for (int i = 0; i < listSize; i++) {
            final Map<?, ?> map = (Map<?, ?>) adapter.getItem(i);
            final String id = (String) map.get(KEY_ID);
            if (defaultId.equals(id)) {
                // If current timezone is in this list, move focus to it
                return i;
            }
        }
        return -1;
    }

    private static void setDate(Context context, int year, int month, int day) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, day);
        long when = c.getTimeInMillis();

        if (when / 1000 < Integer.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    private static void setTime(Context context, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.HOUR_OF_DAY, hourOfDay);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long when = c.getTimeInMillis();

        if (when / 1000 < Integer.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

}
