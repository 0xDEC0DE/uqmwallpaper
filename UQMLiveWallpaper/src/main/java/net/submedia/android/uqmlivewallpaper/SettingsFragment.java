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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "UQMWallpaper.SettingsFragment";
    public static final String ALIEN_RACE = "race";
    public static final String SCALING = "scaling";
    public static final String VERSION = "version";
    public static final String FILL_FRAME = "fillframe";
    static public SharedPreferences prefs;
    private ListPreference mAlien;
    private ListPreference mScaling;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        mAlien = findPreference(ALIEN_RACE);
        mScaling = findPreference(SCALING);
        Preference mVersion = findPreference(VERSION);
        if (mVersion != null)
            mVersion.setSummary(getVersionName(getContext()));

        prefs = getPreferenceManager().getSharedPreferences();
        set_summary_for(mAlien, ALIEN_RACE, prefs);
        set_summary_for(mScaling, SCALING, prefs);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        switch (key) {
            case ALIEN_RACE:
                set_summary_for(mAlien, key, prefs);
                break;
            case SCALING:
                set_summary_for(mScaling, key, prefs);
                break;
        }
        requireActivity().finish();
    }

    private void set_summary_for(ListPreference l, String key, SharedPreferences prefs) {
        String buf;
        if ((buf = prefs.getString(key, null)) != null)
            l.setSummary(get_by_value(l, buf));
    }

    private String get_by_value(ListPreference l, String buf) {
        return (String) l.getEntries()[l.findIndexOfValue(buf)];
    }

    private String getVersionName(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0)
                return pi.versionName;
            else
                return pi.versionName + "-debug";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
