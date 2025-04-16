package net.submedia.android.uqmlivewallpaper;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsActivityTest {

    @Test
    public void testActivityLaunchesAndShowsFragment() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.activity_settings);
                Assert.assertNotNull("SettingsFragment should be present", fragment);
                Assert.assertTrue("Fragment should be instance of SettingsFragment", fragment instanceof SettingsFragment);
            });
        }
    }

    @Test
    public void testRecreationDoesNotAddSecondFragment() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
            scenario.onActivity(activity -> {
                int fragmentCount = activity.getSupportFragmentManager().getFragments().size();
                Assert.assertEquals("Should have exactly one fragment attached", 1, fragmentCount);
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.activity_settings);
                Assert.assertNotNull(fragment);
            });
        }
    }
}
