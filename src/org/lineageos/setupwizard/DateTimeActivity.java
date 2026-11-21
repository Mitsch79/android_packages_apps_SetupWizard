/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.DatePicker;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;


import java.util.Calendar;

public class DateTimeActivity extends DateTimePageHost {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNextText(R.string.next);
        getGlifLayout().setDescriptionText(getString(R.string.date_time_summary));
        bindDateControls(findViewById(R.id.page));
    }

    @Override
    public void onResume() {
        super.onResume();
        startDateControls();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_datetime_page;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_datetime;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_datetime;
    }

    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {

        static final String TAG = TimePickerFragment.class.getSimpleName();

        public static TimePickerFragment newInstance() {
            return new TimePickerFragment();
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            ((DateTimePageHost) requireActivity()).onTimeSet(view, hourOfDay, minute);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Calendar calendar = Calendar.getInstance();
            return new TimePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(getActivity()));
        }
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        static final String TAG = DatePickerFragment.class.getSimpleName();

        public static DatePickerFragment newInstance() {
            return new DatePickerFragment();
        }

        @Override
        public void onDateSet(DatePicker view, int year, int month, int day) {
            ((DateTimePageHost) requireActivity()).onDateSet(view, year, month, day);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Calendar calendar = Calendar.getInstance();
            return new DatePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
        }
    }
}
