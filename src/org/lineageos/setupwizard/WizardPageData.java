/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import android.content.Context;
import android.content.res.Configuration;
import android.provider.Settings;

import com.android.internal.app.LocalePicker;
import com.android.settingslib.datetime.ZoneGetter;

import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/** Prepare data with an application context. The activity creates and owns every view. */
final class WizardPageData {
    private static final Executor WORKER = Executors.newSingleThreadExecutor();
    private static Configuration languageConfig;
    private static boolean developer;
    private static FutureTask<List<LocalePicker.LocaleInfo>> languages;
    private static Configuration zoneConfig;
    private static long zoneDay;
    private static String zoneId;
    private static FutureTask<List<Map<String, Object>>> zones;

    static synchronized FutureTask<List<LocalePicker.LocaleInfo>> languages(Context context) {
        Configuration config = context.getResources().getConfiguration();
        boolean dev = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
        if (!config.equals(languageConfig) || dev != developer) {
            languageConfig = new Configuration(config);
            developer = dev;
            Context app = context.getApplicationContext().createConfigurationContext(languageConfig);
            languages = new FutureTask<>(() -> LocalePicker.getAllAssetLocales(app, dev));
            WORKER.execute(languages);
        }
        return languages;
    }

    static synchronized FutureTask<List<Map<String, Object>>> zones(Context context) {
        Configuration config = context.getResources().getConfiguration();
        Calendar now = Calendar.getInstance();
        long day = now.get(Calendar.YEAR) * 366L + now.get(Calendar.DAY_OF_YEAR);
        String id = now.getTimeZone().getID();
        if (!config.equals(zoneConfig) || day != zoneDay || !id.equals(zoneId)) {
            zoneConfig = new Configuration(config);
            zoneDay = day;
            zoneId = id;
            Context app = context.getApplicationContext().createConfigurationContext(zoneConfig);
            zones = new FutureTask<>(() -> {
                List<Map<String, Object>> rows = ZoneGetter.getZonesList(app);
                rows.sort(Comparator.comparingInt(row -> (Integer) row.get("offset")));
                return rows;
            });
            WORKER.execute(zones);
        }
        return zones;
    }

    static <T> T ready(FutureTask<T> task) {
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}
