/*
 * Copyright (C) 2011 Nicolas Simonds
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package net.submedia.android.uqmlivewallpaper;

import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Encapsulates all user-configurable settings for the UQM Live Wallpaper.
 * Acts as a PreferenceDataStore for the UI and an observable POJO for the engine.
 */
public class WallpaperSettings extends PreferenceDataStore {
    private static final String TAG = "UQMWallpaper.Settings";

    public interface OnSettingsChangedListener {
        void onSettingsChanged(String key);
    }

    public enum State {
        LIVE,       // Active on the home screen, persisted to disk.
        STAGED,     // In-memory draft being edited in Preview mode.
        COMMITTED   // User has confirmed selection, ready for adoption.
    }

    private final CopyOnWriteArrayList<OnSettingsChangedListener> mListeners = new CopyOnWriteArrayList<>();
    private final SharedPreferences mPrefs;

    public String race;
    public float scalingFactor;
    public int offset;
    public boolean fillFrame;

    private State mState;
    private int mTargetFlags = WallpaperManager.FLAG_SYSTEM;

    public WallpaperSettings(@NonNull SharedPreferences prefs) {
        this.mPrefs = prefs;
        this.race = prefs.getString(SettingsFragment.ALIEN_RACE, "urquan");
        this.scalingFactor = prefs.getFloat(SettingsFragment.SCALING_FACTOR, 100.0f);
        this.offset = prefs.getInt(UQMWallpaper.OFFSET_PREF, 0);
        this.fillFrame = prefs.getBoolean(SettingsFragment.FILL_FRAME, false);
        this.mState = State.LIVE;
    }

    private WallpaperSettings(String race, float scalingFactor, int offset, boolean fillFrame, State state, SharedPreferences prefs) {
        this.race = race;
        this.scalingFactor = scalingFactor;
        this.offset = offset;
        this.fillFrame = fillFrame;
        this.mState = state;
        this.mPrefs = prefs;
    }

    public void addListener(OnSettingsChangedListener listener) {
        mListeners.addIfAbsent(listener);
    }

    public void removeListener(OnSettingsChangedListener listener) {
        mListeners.remove(listener);
    }

    private void notifyChanged(String key) {
        for (OnSettingsChangedListener listener : mListeners) listener.onSettingsChanged(key);
    }

    public State getState() {
        return mState;
    }

    public void setState(State state) {
        if (Log.isLoggable(TAG, Log.DEBUG))
            Log.d(TAG, "Settings state transition: %s -> %s".formatted(this.mState, state));
        this.mState = state;
    }

    public int getTargetFlags() {
        return mTargetFlags;
    }

    public void setTargetFlags(int targetFlags) {
        this.mTargetFlags = targetFlags;
    }

    /**
     * Commits the current settings to the provided SharedPreferences editor.
     * This is the only intended mechanism for disk persistence.
     *
     * @param editor The SharedPreferences.Editor to write settings to.
     * @return {@code true} if the commit succeeded, {@code false} otherwise.
     */
    public boolean save(@NonNull SharedPreferences.Editor editor) {
        if (mState != State.LIVE)
            throw new IllegalStateException("Attempted to persist non-LIVE settings: " + mState);
        editor.putString(SettingsFragment.ALIEN_RACE, race);
        editor.putFloat(SettingsFragment.SCALING_FACTOR, scalingFactor);
        editor.putInt(UQMWallpaper.OFFSET_PREF, offset);
        editor.putBoolean(SettingsFragment.FILL_FRAME, fillFrame);
        return editor.commit();
    }

    public void copyFrom(@NonNull WallpaperSettings other) {
        this.mTargetFlags = other.mTargetFlags;
        if (!Objects.equals(this.race, other.race)) {
            this.race = other.race;
            notifyChanged(SettingsFragment.ALIEN_RACE);
        }
        if (Float.compare(this.scalingFactor, other.scalingFactor) != 0) {
            this.scalingFactor = other.scalingFactor;
            notifyChanged(SettingsFragment.SCALING_FACTOR);
        }
        if (this.offset != other.offset) {
            this.offset = other.offset;
            notifyChanged(UQMWallpaper.OFFSET_PREF);
        }
        if (this.fillFrame != other.fillFrame) {
            this.fillFrame = other.fillFrame;
            notifyChanged(SettingsFragment.FILL_FRAME);
        }
    }

    @NonNull
    @Override
    public WallpaperSettings clone() {
        WallpaperSettings cloned = new WallpaperSettings(this.race, this.scalingFactor, this.offset, this.fillFrame, State.STAGED, null);
        cloned.mTargetFlags = this.mTargetFlags;
        return cloned;
    }

    /** --- PreferenceDataStore Implementation ---
     * These methods update the POJO in-memory and notify listeners.
     * Persistence is handled externally during the handoff lifecycle for STAGED settings,
     * or applied immediately for LIVE settings if mPrefs is available.
     */
    private void autoSave(String key, Object value) {
        if (mState == State.LIVE && mPrefs != null) {
            SharedPreferences.Editor editor = mPrefs.edit();
            if (editor == null) return;
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            editor.apply();
        } else if (mState == State.LIVE) {
            Log.w(TAG, "Direct edit to LIVE settings detected but no SharedPreferences available for auto-save.");
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (SettingsFragment.ALIEN_RACE.equals(key)) {
            this.race = value != null ? value : "urquan";
            autoSave(key, this.race);
            notifyChanged(key);
        }
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        if (SettingsFragment.ALIEN_RACE.equals(key)) return this.race;
        return defValue;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (SettingsFragment.FILL_FRAME.equals(key)) {
            this.fillFrame = value;
            autoSave(key, this.fillFrame);
            notifyChanged(key);
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (SettingsFragment.FILL_FRAME.equals(key)) return this.fillFrame;
        return defValue;
    }

    @Override
    public void putFloat(String key, float value) {
        if (SettingsFragment.SCALING_FACTOR.equals(key)) {
            this.scalingFactor = value;
            autoSave(key, this.scalingFactor);
            notifyChanged(key);
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (SettingsFragment.SCALING_FACTOR.equals(key)) return this.scalingFactor;
        return defValue;
    }

    @Override
    public void putInt(String key, int value) {
        if (UQMWallpaper.OFFSET_PREF.equals(key)) {
            this.offset = value;
            autoSave(key, this.offset);
            notifyChanged(key);
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (UQMWallpaper.OFFSET_PREF.equals(key)) return this.offset;
        return defValue;
    }

    // --- Programmatic Setters (for Touch Sync) ---

    public void updateOffset(int offset) {
        if (this.offset != offset) {
            this.offset = offset;
            autoSave(UQMWallpaper.OFFSET_PREF, this.offset);
            notifyChanged(UQMWallpaper.OFFSET_PREF);
        }
    }

    public void updateScalingFactor(float factor) {
        if (this.scalingFactor != factor) {
            this.scalingFactor = factor;
            autoSave(SettingsFragment.SCALING_FACTOR, this.scalingFactor);
            notifyChanged(SettingsFragment.SCALING_FACTOR);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "WallpaperSettings{state=%s, targetFlags=%d, race='%s', scaling=%.1f, offset=%d, fill=%b}"
                .formatted(mState, mTargetFlags, race, scalingFactor, offset, fillFrame);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WallpaperSettings that = (WallpaperSettings) o;
        return Float.compare(that.scalingFactor, scalingFactor) == 0 &&
                offset == that.offset &&
                fillFrame == that.fillFrame &&
                mTargetFlags == that.mTargetFlags &&
                Objects.equals(race, that.race);
    }

    @Override
    public int hashCode() {
        return Objects.hash(race, scalingFactor, offset, fillFrame, mTargetFlags);
    }
}
