/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.location.LocationManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;

public class LocationSettingsActivity extends PaneActivity {
    static final class Page extends WizardPane {
        Page(BaseSetupWizardActivity host) {
            super(host);
        }

        private static final String DRAFT_LOCATION = "draft_location";
        private static final String DRAFT_AGPS = "draft_agps";
        private CheckBox mLocationAccess;
        private CheckBox mLocationAgpsAccess;

        private LocationManager mLocationManager;

        private UserManager mUserManager;

        @Override
        protected void bind() {

            mLocationAccess = findViewById(R.id.location_checkbox);
            mLocationAgpsAccess = findViewById(R.id.location_agps_checkbox);
            mLocationManager = getSystemService(LocationManager.class);
            mUserManager = getSystemService(UserManager.class);
            View locationAccessView = findViewById(R.id.location);
            locationAccessView.setOnClickListener(
                    v -> mLocationAccess.setChecked(!mLocationAccess.isChecked()));
            View locationAgpsAccessView = findViewById(R.id.location_agps);
            if (mUserManager.isMainUser()) {
                locationAgpsAccessView.setOnClickListener(
                        v -> mLocationAgpsAccess.setChecked(!mLocationAgpsAccess.isChecked()));
            } else {
                locationAgpsAccessView.setVisibility(View.GONE);
            }
        }

        @Override
        protected void refresh() {
            boolean checked = mLocationManager.isLocationEnabled();
            if (mUserManager.isManagedProfile()) {
                checked &= mUserManager.hasUserRestriction(UserManager.DISALLOW_SHARE_LOCATION);
            }
            Bundle draft = SetupWizardApp.getSettingsBundle();
            mLocationAccess.setChecked(draft.getBoolean(DRAFT_LOCATION, checked));
            mLocationAgpsAccess.setChecked(draft.getBoolean(DRAFT_AGPS, Settings.Global.getInt(
                    getContentResolver(), Settings.Global.ASSISTED_GPS_ENABLED,
                    mLocationAgpsAccess.isChecked() ? 1 : 0) != 0));
            mLocationAccess.setOnCheckedChangeListener((button, value) ->
                    draft.putBoolean(DRAFT_LOCATION, value));
            mLocationAgpsAccess.setOnCheckedChangeListener((button, value) ->
                    draft.putBoolean(DRAFT_AGPS, value));
        }

        @Override
        protected void onNextPressed() {
            mLocationManager.setLocationEnabledForUser(mLocationAccess.isChecked(),
                    Process.myUserHandle());
            if (mUserManager.isManagedProfile()) {
                mUserManager.setUserRestriction(UserManager.DISALLOW_SHARE_LOCATION,
                        !mLocationAccess.isChecked());
            }
            Settings.Global.putInt(getContentResolver(), Settings.Global.ASSISTED_GPS_ENABLED,
                    mLocationAgpsAccess.isChecked() ? 1 : 0);
            super.onNextPressed();
        }

        @Override
        protected int getLayoutResId() {
            return R.layout.location_settings;
        }

        @Override
        protected int getTitleResId() {
            return R.string.setup_location;
        }

        @Override
        protected int getIconResId() {
            return R.drawable.ic_location;
        }
    }
}
