package net.submedia.android.uqmlivewallpaper;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.util.Log;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class WallpaperSettingsTest extends BaseTest {

    @Mock
    private SharedPreferences mockPrefs;
    @Mock
    private SharedPreferences.Editor mockEditor;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Provide default values for mock SharedPreferences
        when(mockPrefs.getString(anyString(), anyString())).thenReturn("urquan");
        when(mockPrefs.getFloat(anyString(), anyFloat())).thenReturn(100.0f);
        when(mockPrefs.getInt(anyString(), anyInt())).thenReturn(0);
        when(mockPrefs.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor);
        when(mockEditor.putFloat(anyString(), anyFloat())).thenReturn(mockEditor);
        when(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor);
    }

    @Test
    public void testConstructor_loadsFromPrefs() {
        String race = createString();
        float scaling = rand.nextFloat() * 100;
        int offset = rand.nextInt(1000) - 500;
        boolean fillFrame = rand.nextBoolean();
        when(mockPrefs.getString(eq(SettingsFragment.ALIEN_RACE), anyString())).thenReturn(race);
        when(mockPrefs.getFloat(eq(SettingsFragment.SCALING_FACTOR), anyFloat())).thenReturn(scaling);
        when(mockPrefs.getInt(eq(UQMWallpaper.OFFSET_PREF), anyInt())).thenReturn(offset);
        when(mockPrefs.getBoolean(eq(SettingsFragment.FILL_FRAME), anyBoolean())).thenReturn(fillFrame);
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        Assert.assertEquals(race, settings.race);
        Assert.assertEquals(scaling, settings.scalingFactor, 0.001f);
        Assert.assertEquals(offset, settings.offset);
        Assert.assertEquals(fillFrame, settings.fillFrame);
        Assert.assertEquals(WallpaperSettings.State.LIVE, settings.getState());
    }

    @Test
    public void testSave_updatesEditor() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        String race = createString();
        float scaling = rand.nextFloat() * 100;
        int offset = rand.nextInt(1000) - 500;
        boolean fillFrame = rand.nextBoolean();
        settings.race = race;
        settings.scalingFactor = scaling;
        settings.offset = offset;
        settings.fillFrame = fillFrame;
        settings.save(mockEditor);
        verify(mockEditor).putString(SettingsFragment.ALIEN_RACE, race);
        verify(mockEditor).putFloat(SettingsFragment.SCALING_FACTOR, scaling);
        verify(mockEditor).putInt(UQMWallpaper.OFFSET_PREF, offset);
        verify(mockEditor).putBoolean(SettingsFragment.FILL_FRAME, fillFrame);
    }

    @Test(expected = IllegalStateException.class)
    public void testSave_throwsOnNonLiveState() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        settings.setState(WallpaperSettings.State.STAGED);
        settings.save(mockEditor);
    }

    @Test
    public void testClone_createsDeepCopy_SetsStagedState() {
        WallpaperSettings original = new WallpaperSettings(mockPrefs);
        original.race = createString();
        original.offset = rand.nextInt(1000) - 500;
        original.setTargetFlags(WallpaperManager.FLAG_LOCK);
        WallpaperSettings clone = original.clone();
        Assert.assertEquals(original.race, clone.race);
        Assert.assertEquals(original.offset, clone.offset);
        Assert.assertEquals(original.scalingFactor, clone.scalingFactor, 0.001f);
        Assert.assertEquals(original.fillFrame, clone.fillFrame);
        Assert.assertEquals(WallpaperManager.FLAG_LOCK, clone.getTargetFlags());
        Assert.assertNotSame(original, clone);
        Assert.assertEquals(WallpaperSettings.State.STAGED, clone.getState());
        clone.race = createString() + "_modified";
        Assert.assertNotEquals(original.race, clone.race);
    }

    @Test
    public void testPreferenceDataStore_UpdatesAndNotifies_AutoPersistenceForLive() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        settings.setState(WallpaperSettings.State.LIVE);
        WallpaperSettings.OnSettingsChangedListener listener = mock(WallpaperSettings.OnSettingsChangedListener.class);
        settings.addListener(listener);
        // Test String (Race)
        String race = createString();
        settings.putString(SettingsFragment.ALIEN_RACE, race);
        Assert.assertEquals(race, settings.race);
        verify(listener).onSettingsChanged(SettingsFragment.ALIEN_RACE);
        verify(mockEditor).putString(SettingsFragment.ALIEN_RACE, race);
        verify(mockEditor).apply();
        // Test Boolean (Fill Frame)
        boolean fillFrame = rand.nextBoolean();
        settings.putBoolean(SettingsFragment.FILL_FRAME, fillFrame);
        Assert.assertEquals(fillFrame, settings.fillFrame);
        verify(listener).onSettingsChanged(SettingsFragment.FILL_FRAME);
        verify(mockEditor).putBoolean(SettingsFragment.FILL_FRAME, fillFrame);
        // Test Float (Scaling)
        float scaling = rand.nextFloat() * 100;
        settings.putFloat(SettingsFragment.SCALING_FACTOR, scaling);
        Assert.assertEquals(scaling, settings.scalingFactor, 0.001f);
        verify(listener).onSettingsChanged(SettingsFragment.SCALING_FACTOR);
        verify(mockEditor).putFloat(SettingsFragment.SCALING_FACTOR, scaling);
        // Test Int (Offset)
        int offset = rand.nextInt(1000) - 500;
        settings.putInt(UQMWallpaper.OFFSET_PREF, offset);
        Assert.assertEquals(offset, settings.offset);
        verify(listener).onSettingsChanged(UQMWallpaper.OFFSET_PREF);
        verify(mockEditor).putInt(UQMWallpaper.OFFSET_PREF, offset);
    }

    @Test
    public void testAutoSave_NullEditor() {
        when(mockPrefs.edit()).thenReturn(null);
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        settings.setState(WallpaperSettings.State.LIVE);
        settings.putString(SettingsFragment.ALIEN_RACE, createString());
        verify(mockPrefs).edit();
        // There are two error paths out of this method, ensure we took the one that doesn't log
        mockedStaticLog.verify(() -> Log.w(anyString(), anyString()), never());
    }

    @Test
    public void testAutoSave_NullPrefs() {
        // We need a settings object with null mPrefs.  The clone() method creates one.
        WallpaperSettings settings = new WallpaperSettings(mockPrefs).clone();
        settings.setState(WallpaperSettings.State.LIVE); // Force LIVE state on a cloned object
        settings.putString(SettingsFragment.ALIEN_RACE, createString());
        mockedStaticLog.verify(() -> Log.w(eq("UQMWallpaper.Settings"), matches("no SharedPreferences available")));
    }

    @Test
    public void testDirectEditWarnings() {
        // Cloned (STAGED) settings DON'T auto-save
        WallpaperSettings settings = new WallpaperSettings(mockPrefs).clone();
        Assert.assertEquals(WallpaperSettings.State.STAGED, settings.getState());
        settings.putString(SettingsFragment.ALIEN_RACE, createString());
        verify(mockPrefs, times(0)).edit(); // Clone has null prefs, shouldn't save
    }

    @Test
    public void testPutString_NullValue_SetsDefault() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        settings.setState(WallpaperSettings.State.STAGED);
        settings.race = createString();
        settings.putString(SettingsFragment.ALIEN_RACE, null);
        Assert.assertEquals("urquan", settings.race);
    }

    @Test
    public void testPreferenceDataStore_Getters() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        String randomRace = createString();
        float randomScaling = rand.nextFloat() * 100;
        int randomOffset = rand.nextInt(1000) - 500;
        boolean randomFill = rand.nextBoolean();
        settings.race = randomRace;
        settings.scalingFactor = randomScaling;
        settings.offset = randomOffset;
        settings.fillFrame = randomFill;
        Assert.assertEquals(randomRace, settings.getString(SettingsFragment.ALIEN_RACE, "default"));
        Assert.assertEquals("default", settings.getString("unknown_key", "default"));
        Assert.assertEquals(randomScaling, settings.getFloat(SettingsFragment.SCALING_FACTOR, 0.0f), 0.001f);
        Assert.assertEquals(0.0f, settings.getFloat("unknown_key", 0.0f), 0.001f);
        Assert.assertEquals(randomOffset, settings.getInt(UQMWallpaper.OFFSET_PREF, 0));
        Assert.assertEquals(0, settings.getInt("unknown_key", 0));
        Assert.assertEquals(randomFill, settings.getBoolean(SettingsFragment.FILL_FRAME, !randomFill));
        Assert.assertFalse(settings.getBoolean("unknown_key", false));
    }

    @Test
    public void testCopyFrom_UpdatesAndNotifies() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        WallpaperSettings other = settings.clone();
        other.race = createString();
        other.offset = rand.nextInt(1000) - 500;
        other.scalingFactor = rand.nextFloat() * 100;
        other.fillFrame = !settings.fillFrame;
        other.setTargetFlags(WallpaperManager.FLAG_LOCK);
        WallpaperSettings.OnSettingsChangedListener listener = mock(WallpaperSettings.OnSettingsChangedListener.class);
        settings.addListener(listener);
        settings.copyFrom(other);
        Assert.assertEquals(other.race, settings.race);
        Assert.assertEquals(other.offset, settings.offset);
        Assert.assertEquals(other.scalingFactor, settings.scalingFactor, 0.001f);
        Assert.assertEquals(other.fillFrame, settings.fillFrame);
        Assert.assertEquals(WallpaperManager.FLAG_LOCK, settings.getTargetFlags());
        verify(listener).onSettingsChanged(SettingsFragment.ALIEN_RACE);
        verify(listener).onSettingsChanged(UQMWallpaper.OFFSET_PREF);
        verify(listener).onSettingsChanged(SettingsFragment.SCALING_FACTOR);
        verify(listener).onSettingsChanged(SettingsFragment.FILL_FRAME);
    }

    @Test
    public void testCopyFrom_NoChanges_DoesNotNotify() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        WallpaperSettings other = settings.clone();
        WallpaperSettings.OnSettingsChangedListener listener = mock(WallpaperSettings.OnSettingsChangedListener.class);
        settings.addListener(listener);
        settings.copyFrom(other);
        verifyNoInteractions(listener);
    }

    @Test
    public void testProgrammaticSetters_NotifiesAndSavesForLive() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        settings.offset = rand.nextInt();
        settings.scalingFactor = rand.nextFloat() * 50.0f;
        WallpaperSettings.OnSettingsChangedListener listener = mock(WallpaperSettings.OnSettingsChangedListener.class);
        settings.addListener(listener);
        // Change value -> notify and save
        int newOffset = settings.offset + rand.nextInt();
        settings.updateOffset(newOffset);
        Assert.assertEquals(newOffset, settings.offset);
        verify(listener).onSettingsChanged(UQMWallpaper.OFFSET_PREF);
        verify(mockEditor).putInt(UQMWallpaper.OFFSET_PREF, newOffset);
        // Same value -> no notify
        settings.updateOffset(newOffset);
        verify(listener, times(1)).onSettingsChanged(UQMWallpaper.OFFSET_PREF);
        float newScaling = settings.scalingFactor + (rand.nextInt() * 50.0f);
        settings.updateScalingFactor(newScaling);
        verify(listener).onSettingsChanged(SettingsFragment.SCALING_FACTOR);
        verify(mockEditor).putFloat(SettingsFragment.SCALING_FACTOR, newScaling);
        // Same scaling factor -> no notify
        settings.updateScalingFactor(newScaling);
        verify(listener, times(1)).onSettingsChanged(SettingsFragment.SCALING_FACTOR);
    }

    @Test
    public void testRemoveListener() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        WallpaperSettings.OnSettingsChangedListener listener = mock(WallpaperSettings.OnSettingsChangedListener.class);
        settings.addListener(listener);
        settings.removeListener(listener);
        settings.setState(WallpaperSettings.State.STAGED);
        settings.putString(SettingsFragment.ALIEN_RACE, createString());
        verifyNoInteractions(listener);
    }

    @Test
    public void testToString() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        settings.race = createString();
        settings.scalingFactor = rand.nextFloat() * 100.0f;
        settings.offset = rand.nextInt();
        settings.fillFrame = rand.nextBoolean();
        settings.setTargetFlags(WallpaperManager.FLAG_SYSTEM);
        settings.setState(WallpaperSettings.State.LIVE);
        String expected = "WallpaperSettings{state=LIVE, targetFlags=1, race='%s', scaling=%.1f, offset=%d, fill=%b}".formatted(
                settings.race, settings.scalingFactor, settings.offset, settings.fillFrame
        );
        Assert.assertEquals(expected, settings.toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        WallpaperSettings settings1 = new WallpaperSettings(mockPrefs);
        WallpaperSettings settings2 = new WallpaperSettings(mockPrefs);
        Assert.assertEquals(settings1, settings2);
        Assert.assertEquals(settings1.hashCode(), settings2.hashCode());
        settings2.race = createString();
        Assert.assertNotEquals(settings1, settings2);
        Assert.assertNotEquals(settings1.hashCode(), settings2.hashCode());
        settings2.race = settings1.race;
        settings2.offset = 100;
        Assert.assertNotEquals(settings1, settings2);
        settings2.offset = settings1.offset;
        settings2.scalingFactor = 50.0f;
        Assert.assertNotEquals(settings1, settings2);
        settings2.scalingFactor = settings1.scalingFactor;
        settings2.fillFrame = !settings1.fillFrame;
        Assert.assertNotEquals(settings1, settings2);
        settings2.fillFrame = settings1.fillFrame;
        settings2.setTargetFlags(WallpaperManager.FLAG_LOCK);
        Assert.assertNotEquals(settings1, settings2);
        Assert.assertNotEquals(new Object(), settings1);
        Assert.assertNotEquals(null, settings1);
    }

    @Test
    public void testSetAndGetState() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        settings.setState(WallpaperSettings.State.COMMITTED);
        Assert.assertEquals(WallpaperSettings.State.COMMITTED, settings.getState());
        mockedStaticLog.verify(() -> Log.d(eq("UQMWallpaper.Settings"), matches("Settings state transition")));
    }
}
