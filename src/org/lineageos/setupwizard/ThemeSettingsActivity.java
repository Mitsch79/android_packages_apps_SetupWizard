/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.widget.RadioGroup;


public class ThemeSettingsActivity extends PaneActivity {
    static final class Page extends WizardPane {
        Page(BaseSetupWizardActivity host) {
            super(host);
        }

        @Override
        protected void bind() {
            getGlifLayout().setDescriptionText(getString(R.string.theme_summary));

            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            final RadioGroup radioGroup = findViewById(R.id.theme_radio_group);
            radioGroup.check(((getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
                    ? R.id.radio_dark : R.id.radio_light);
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                switch (checkedId) {
                    case R.id.radio_dark:
                        uiModeManager.setNightModeActivated(true);
                        break;
                    case R.id.radio_light:
                        uiModeManager.setNightModeActivated(false);
                        break;
                }
            });
        }

        @Override
        protected int getLayoutResId() {
            return R.layout.setup_theme;
        }

        @Override
        protected int getTitleResId() {
            return R.string.setup_theme;
        }

        @Override
        protected int getIconResId() {
            return R.drawable.ic_theme;
        }
    }
}
