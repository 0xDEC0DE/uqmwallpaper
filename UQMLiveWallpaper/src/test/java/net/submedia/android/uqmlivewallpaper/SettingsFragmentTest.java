package net.submedia.android.uqmlivewallpaper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsFragmentTest {

    private Context context;
    private SettingsFragment fragment;
    private FragmentActivity activity;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        activity = Robolectric.buildActivity(FragmentActivity.class)
                .create()
                .start()
                .resume()
                .get();
        fragment = new SettingsFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment, "settings")
                .commitNow();
    }

    @Test
    public void testGetVersionName() {
        String version = SettingsFragment.getVersionName();
        // In unit tests with Robolectric, BuildConfig values usually reflect the manifest or defaults.
        // We just want to ensure it doesn't crash and returns something plausible.
        Assert.assertNotNull(version);
        Assert.assertTrue(version.length() > 0);
    }

    @Test
    public void testConfigurePreference_ListPreference() {
        ListPreference mockPreference = mock(ListPreference.class);
        Runnable mockRunnable = mock(Runnable.class);
        SettingsFragment.configurePreference(mockPreference, mockRunnable);
        verify(mockPreference).setSummaryProvider(any());
        ArgumentCaptor<Preference.OnPreferenceChangeListener> captor = ArgumentCaptor.forClass(Preference.OnPreferenceChangeListener.class);
        verify(mockPreference).setOnPreferenceChangeListener(captor.capture());
        boolean result = captor.getValue().onPreferenceChange(mockPreference, "newValue");
        Assert.assertTrue(result);
        verify(mockRunnable).run();
    }

    @Test
    public void testConfigurePreference_NormalPreference() {
        Preference mockPreference = mock(Preference.class);
        Runnable mockRunnable = mock(Runnable.class);
        SettingsFragment.configurePreference(mockPreference, mockRunnable);
        verify(mockPreference, never()).setSummaryProvider(any());
        ArgumentCaptor<Preference.OnPreferenceChangeListener> captor = ArgumentCaptor.forClass(Preference.OnPreferenceChangeListener.class);
        verify(mockPreference).setOnPreferenceChangeListener(captor.capture());
        boolean result = captor.getValue().onPreferenceChange(mockPreference, "newValue");
        Assert.assertTrue(result);
        verify(mockRunnable).run();
    }

    @Test
    public void testConfigurePreference_NullPreference() {
        Runnable mockRunnable = mock(Runnable.class);
        SettingsFragment.configurePreference(null, mockRunnable);
        verify(mockRunnable, never()).run();
    }

    @Test
    public void testConfigurePreference_NullAction() {
        Preference mockPreference = mock(Preference.class);
        SettingsFragment.configurePreference(mockPreference, null);
        ArgumentCaptor<Preference.OnPreferenceChangeListener> captor = ArgumentCaptor.forClass(Preference.OnPreferenceChangeListener.class);
        verify(mockPreference).setOnPreferenceChangeListener(captor.capture());
        boolean result = captor.getValue().onPreferenceChange(mockPreference, "newValue");
        Assert.assertTrue(result);
    }

    @Test
    public void testPreferencesExist() {
        Assert.assertNotNull(fragment.findPreference(SettingsFragment.ALIEN_RACE));
        Assert.assertNotNull(fragment.findPreference(SettingsFragment.SCALING));
        Assert.assertNotNull(fragment.findPreference(SettingsFragment.FILL_FRAME));
        Assert.assertNotNull(fragment.findPreference(SettingsFragment.VERSION));
    }

    @Test
    public void testVersionPreferenceSummarySet() {
        Preference versionPref = fragment.findPreference(SettingsFragment.VERSION);
        Assert.assertNotNull(versionPref);
        Assert.assertNotNull(versionPref.getSummary());
    }

    @Test
    public void testPreferenceChangeFinishesActivity() {
        Preference preference = fragment.findPreference(SettingsFragment.ALIEN_RACE);
        Assert.assertNotNull(preference);
        Preference.OnPreferenceChangeListener listener = preference.getOnPreferenceChangeListener();
        Assert.assertNotNull(listener);
        boolean result = listener.onPreferenceChange(preference, "urquan");
        Assert.assertTrue(result);
        Assert.assertTrue(activity.isFinishing());
    }

    @Test
    public void testOnCreatePreferences_WithStagedSettings() throws Exception {
        WallpaperSettings mockStaged = mock(WallpaperSettings.class);
        Field field = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        field.setAccessible(true);
        field.set(null, mockStaged);
        SettingsFragment testFragment = new SettingsFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, testFragment)
                .commitNow();
        Assert.assertSame(mockStaged, getDataStore(testFragment));
        field.set(null, null); // cleanup
    }

    @Test
    public void testOnCreatePreferences_WithLiveSettings() throws Exception {
        Field stagedField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedField.setAccessible(true);
        stagedField.set(null, null);
        WallpaperSettings mockLive = mock(WallpaperSettings.class);
        Field liveField = UQMWallpaper.class.getDeclaredField("sLiveHomeSettings");
        liveField.setAccessible(true);
        liveField.set(null, mockLive);
        SettingsFragment testFragment = new SettingsFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, testFragment)
                .commitNow();
        Assert.assertSame(mockLive, getDataStore(testFragment));
        liveField.set(null, null); // cleanup
    }

    @Test
    public void testOnCreatePreferences_WithLiveLockSettings() throws Exception {
        Field stagedField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedField.setAccessible(true);
        stagedField.set(null, null);
        WallpaperSettings mockLiveLock = mock(WallpaperSettings.class);
        Field liveField = UQMWallpaper.class.getDeclaredField("sLiveLockSettings");
        liveField.setAccessible(true);
        liveField.set(null, mockLiveLock);
        Intent intent = new Intent();
        intent.putExtra(SettingsActivity.EXTRA_TARGET_FLAGS, WallpaperManager.FLAG_LOCK);
        activity.setIntent(intent);
        SettingsFragment testFragment = new SettingsFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, testFragment)
                .commitNow();
        Assert.assertSame(mockLiveLock, getDataStore(testFragment));
        liveField.set(null, null); // cleanup
    }

    private Object getDataStore(PreferenceFragmentCompat fragment) throws Exception {
        PreferenceManager manager = fragment.getPreferenceManager();
        Method method = PreferenceManager.class.getDeclaredMethod("getPreferenceDataStore");
        method.setAccessible(true);
        return method.invoke(manager);
    }
}
