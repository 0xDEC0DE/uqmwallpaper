/*
 * Copyright (c) 2021 Nicolas Simonds
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package net.submedia.android.uqmlivewallpaper;

import android.app.WallpaperManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String ALIEN_RACE = "race";
    public static final String SCALING = "scaling";
    public static final String SCALING_FACTOR = "scalingfactor";
    public static final String VERSION = "version";
    public static final String FILL_FRAME = "fillframe";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        // Use the staged settings from the Wallpaper service as the data store
        WallpaperSettings staged = UQMWallpaper.getStagedSettings();
        if (staged != null) {
            getPreferenceManager().setPreferenceDataStore(staged);
        } else {
            // If no staged session is active, edit the live settings directly
            int targetFlags = requireActivity().getIntent().getIntExtra(SettingsActivity.EXTRA_TARGET_FLAGS, WallpaperManager.FLAG_SYSTEM);

            // Belt-and-braces: mask off invalid values from target flags
            targetFlags &= WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;

            WallpaperSettings live = UQMWallpaper.getLiveSettings(targetFlags);
            if (live != null) getPreferenceManager().setPreferenceDataStore(live);
        }

        setPreferencesFromResource(R.xml.settings, rootKey);

        setupPreference(ALIEN_RACE);
        setupPreference(SCALING);
        setupPreference(FILL_FRAME);
        setupVersionPreference();
    }

    protected void setupPreference(String key) {
        Preference preference = findPreference(key);
        configurePreference(preference, () -> requireActivity().finish());
    }

    @VisibleForTesting
    static void configurePreference(Preference preference, Runnable onChangeAction) {
        if (preference != null) {
            if (preference instanceof ListPreference)
                preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                if (onChangeAction != null) onChangeAction.run();
                return true;
            });
        }
    }

    protected void setupVersionPreference() {
        Preference versionPref = findPreference(VERSION);
        if (versionPref != null) versionPref.setSummary(getVersionName());
    }

    @VisibleForTesting
    static String getVersionName() {
        return BuildConfig.VERSION_NAME + (BuildConfig.DEBUG ? "-debug" : "");
    }
}
