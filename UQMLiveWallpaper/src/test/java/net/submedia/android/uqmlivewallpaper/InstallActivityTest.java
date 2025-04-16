package net.submedia.android.uqmlivewallpaper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class InstallActivityTest extends BaseTest {

    @Test
    public void testActivityLaunchesWallpaperPicker() {
        try (ActivityScenario<InstallActivity> scenario = ActivityScenario.launch(InstallActivity.class)) {
            scenario.onActivity(activity -> {
                ShadowActivity shadowActivity = Shadows.shadowOf(activity);
                Intent nextStartedActivity = shadowActivity.getNextStartedActivity();
                Assert.assertNotNull("Should have started a new activity", nextStartedActivity);
                Assert.assertEquals("Should launch live wallpaper picker",
                        WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER, nextStartedActivity.getAction());
                ComponentName componentName = nextStartedActivity.getParcelableExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName.class);
                Assert.assertNotNull("Should provide component name extra", componentName);
                Assert.assertEquals(UQMWallpaper.class.getName(), componentName.getClassName());
            });
        }
    }

    @Test
    public void testActivityFallsBackToChooserOnException() throws Exception {
        InstallActivity activity = Robolectric.buildActivity(InstallActivity.class).get();
        ActivityResultLauncher<Intent> mockLauncher = mock(ActivityResultLauncher.class);
        doThrow(new ActivityNotFoundException()).when(mockLauncher).launch(argThat(intent ->
                intent != null && WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER.equals(intent.getAction())));
        injectMockLauncher(activity, mockLauncher);
        activity.onCreate(null);
        verify(mockLauncher).launch(argThat(intent ->
                intent != null && WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER.equals(intent.getAction())));
    }

    @Test
    public void testActivityShowsToastWhenBothFail() throws Exception {
        InstallActivity activity = Robolectric.buildActivity(InstallActivity.class).get();
        @SuppressWarnings("unchecked")
        ActivityResultLauncher<Intent> mockLauncher = mock(ActivityResultLauncher.class);
        doThrow(new ActivityNotFoundException()).when(mockLauncher).launch(any());
        injectMockLauncher(activity, mockLauncher);
        activity.onCreate(null);
        Assert.assertTrue("Activity should be finishing", activity.isFinishing());
        String latestToast = ShadowToast.getTextOfLatestToast();
        Assert.assertNotNull("A toast should be shown", latestToast);
        Assert.assertEquals(ApplicationProvider.getApplicationContext().getString(R.string.unsupported_device), latestToast);
    }

    private void injectMockLauncher(InstallActivity activity, ActivityResultLauncher<Intent> mockLauncher) throws Exception {
        Field field = InstallActivity.class.getDeclaredField("wallpaperLauncher");
        field.setAccessible(true);
        field.set(activity, mockLauncher);
    }

    @Test
    public void testActivityFinishesAfterResult() {
        try (ActivityScenario<InstallActivity> scenario = ActivityScenario.launch(InstallActivity.class)) {
            scenario.onActivity(activity -> {
                // Trigger the result manually using the activity's registry
                activity.getActivityResultRegistry().dispatchResult(
                        Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode,
                        Activity.RESULT_OK,
                        null);
            });
            scenario.onActivity(activity -> Assert.assertTrue("Activity should be finishing after result", activity.isFinishing()));
        }
    }

    @Test
    public void testRecreationDoesNotRelaunch() {
        try (ActivityScenario<InstallActivity> scenario = ActivityScenario.launch(InstallActivity.class)) {
            scenario.onActivity(activity -> {
                ShadowActivity shadowActivity = Shadows.shadowOf(activity);
                shadowActivity.getNextStartedActivity(); // clear the first one
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                ShadowActivity shadowActivity = Shadows.shadowOf(activity);
                Intent nextIntent = shadowActivity.getNextStartedActivity();
                Assert.assertNull("Should not launch picker again on recreation", nextIntent);
            });
        }
    }
}
