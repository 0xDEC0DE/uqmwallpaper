package net.submedia.android.uqmlivewallpaper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.os.OperationCanceledException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UQMWallpaperTest extends BaseTest {

    private UQMWallpaper wallpaperService;
    private UQMWallpaper.CommsEngine engine;

    @Mock
    private SurfaceHolder mockSurfaceHolder;
    @Mock
    private Surface mockSurface;
    @Mock
    private Canvas mockCanvas;
    @Mock
    private UQMWallpaper.AnimationFactory mockAnimationFactory;
    @Mock
    private Animation mockAnimation;
    @Mock
    private Bitmap mockFrame;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Field stagedField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedField.setAccessible(true);
        stagedField.set(null, null);
        when(mockAnimation.getFrame()).thenReturn(mockFrame);
        when(mockFrame.getWidth()).thenReturn(640);
        when(mockFrame.getHeight()).thenReturn(480);
        when(mockAnimationFactory.create(anyString(), any(Context.class), any())).thenReturn(mockAnimation);
        ServiceController<UQMWallpaper> controller = Robolectric.buildService(UQMWallpaper.class);
        wallpaperService = controller.get();
        wallpaperService.setAnimationFactory(mockAnimationFactory);
        controller.create();
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        engine = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
    }

    @Test
    public void testOnCreate_initializesService() {
        Assert.assertNotNull(wallpaperService);
    }

    @Test
    public void testMigrateLegacyScaling_NoScaling() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(prefs.contains(SettingsFragment.SCALING)).thenReturn(true);
        when(prefs.contains(SettingsFragment.SCALING_FACTOR)).thenReturn(false);
        when(prefs.getString(SettingsFragment.SCALING, "2")).thenReturn("0");
        when(prefs.edit()).thenReturn(editor);
        when(editor.putFloat(anyString(), anyFloat())).thenReturn(editor);
        when(editor.remove(anyString())).thenReturn(editor);
        UQMWallpaper.migrateLegacyScaling(prefs);
        verify(editor).putFloat(SettingsFragment.SCALING_FACTOR, 0.0f);
        verify(editor).remove(SettingsFragment.SCALING);
        verify(editor).apply();
    }

    @Test
    public void testMigrateLegacyScaling_FitScreen() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(prefs.contains(SettingsFragment.SCALING)).thenReturn(true);
        when(prefs.contains(SettingsFragment.SCALING_FACTOR)).thenReturn(false);
        when(prefs.getString(SettingsFragment.SCALING, "2")).thenReturn("1");
        when(prefs.edit()).thenReturn(editor);
        when(editor.putFloat(anyString(), anyFloat())).thenReturn(editor);
        when(editor.remove(anyString())).thenReturn(editor);
        UQMWallpaper.migrateLegacyScaling(prefs);
        verify(editor).putFloat(SettingsFragment.SCALING_FACTOR, 100.0f);
        verify(editor).remove(SettingsFragment.SCALING);
        verify(editor).apply();
    }

    @Test
    public void testMigrateLegacyScaling_FitVirtual() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(prefs.contains(SettingsFragment.SCALING)).thenReturn(true);
        when(prefs.contains(SettingsFragment.SCALING_FACTOR)).thenReturn(false);
        when(prefs.getString(SettingsFragment.SCALING, "2")).thenReturn("2");
        when(prefs.edit()).thenReturn(editor);
        when(editor.putFloat(anyString(), anyFloat())).thenReturn(editor);
        when(editor.remove(anyString())).thenReturn(editor);
        UQMWallpaper.migrateLegacyScaling(prefs);
        verify(editor).putFloat(SettingsFragment.SCALING_FACTOR, 100.0f);
        verify(editor).remove(SettingsFragment.SCALING);
        verify(editor).apply();
    }

    @Test
    public void testMigrateLegacyScaling_AlreadyMigrated() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.contains(SettingsFragment.SCALING)).thenReturn(true);
        when(prefs.contains(SettingsFragment.SCALING_FACTOR)).thenReturn(true);
        UQMWallpaper.migrateLegacyScaling(prefs);
        verify(prefs, never()).edit();
    }

    @Test
    public void testOnCreateEngine_returnsCommsEngine() {
        Assert.assertNotNull(engine);
    }

    @Test
    public void testOnCreateEngine_multiEngineLogic() {
        UQMWallpaper.CommsEngine engine1 = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        UQMWallpaper.CommsEngine engine2 = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        Assert.assertNotEquals(engine1, engine2);
    }

    @Test
    public void testSetTotalWidth_updatesAllEngines() {
        UQMWallpaper.CommsEngine engine1 = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        UQMWallpaper.CommsEngine engine2 = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        int newWidth = rand.nextInt(1000, 5001);
        wallpaperService.setTotalWidth(newWidth);
        Assert.assertEquals(newWidth, engine1.getViewModel().getTotalWidth());
        Assert.assertEquals(newWidth, engine2.getViewModel().getTotalWidth());
    }

    @Test
    public void testOnDestroy_removesFromActiveEngines() {
        UQMWallpaper.CommsEngine engine1 = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        int initialCount = getActiveEngineCount();
        engine1.onDestroy();
        Assert.assertEquals(initialCount - 1, getActiveEngineCount());
    }

    private int getActiveEngineCount() {
        try {
            Field field = UQMWallpaper.class.getDeclaredField("mActiveEngines");
            field.setAccessible(true);
            java.util.List<?> engines = (java.util.List<?>) field.get(wallpaperService);
            return engines != null ? engines.size() : 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testOnSurfaceChanged_updatesDimensions() {
        int width = rand.nextInt(1080);
        int height = rand.nextInt(1920);
        engine.onSurfaceChanged(mockSurfaceHolder, 0, width, height);
        Assert.assertEquals(width, engine.getViewModel().getWidth());
        Assert.assertEquals(height, engine.getViewModel().getHeight());
    }

    @Test
    public void testUpdateAspect_calculatesCorrectly() {
        int totalWidth = rand.nextInt(2160);
        float scalingFactor = rand.nextFloat() * 50.0f;
        int originalWidth = mockFrame.getWidth();
        wallpaperService.setTotalWidth(totalWidth);
        engine.getViewModel().setScalingFactor(scalingFactor);
        engine.getViewModel().updateAspect(mockFrame);
        float targetWidth = originalWidth + (scalingFactor / 100.0f) * (totalWidth - originalWidth);
        int expectedAspect = (int) (targetWidth * 10000 / originalWidth);
        Assert.assertEquals(expectedAspect, engine.getViewModel().getAspect());
    }

    @Test
    public void testOnOffsetsChanged_calculatesOffsetInPortrait() {
        int totalWidth = 2160;
        int screenWidth = 1080;
        int userOffset = -100;
        float xOffset = 0.5f;
        wallpaperService.setTotalWidth(totalWidth);
        WallpaperSettings settings = getSettingsFromEngine(engine);
        settings.offset = userOffset;
        settings.scalingFactor = 100f;
        engine.onSettingsChanged(UQMWallpaper.OFFSET_PREF);
        engine.onSettingsChanged(SettingsFragment.SCALING_FACTOR);
        engine.onSurfaceChanged(mockSurfaceHolder, 0, screenWidth, 1920);
        engine.getViewModel().updateAspect(mockFrame);
        Resources res = wallpaperService.getResources();
        Configuration config = res.getConfiguration();
        config.orientation = Configuration.ORIENTATION_PORTRAIT;
        engine.onOffsetsChanged(xOffset, 0, 0.1f, 0, 0, 0);
        // ExpectedOffset = -100 + (1080 - 2160 - (-100)) * 0.5 = -590
        Assert.assertEquals(-590, engine.getViewModel().getOffset());
    }

    @Test
    public void testOnOffsetsChanged_resetsOffsetInLandscape() {
        Resources res = wallpaperService.getResources();
        Configuration config = res.getConfiguration();
        config.orientation = Configuration.ORIENTATION_PORTRAIT;
        wallpaperService.setTotalWidth(2160);
        engine.getViewModel().setScalingFactor(100f);
        engine.onSurfaceChanged(mockSurfaceHolder, 0, 1080, 1920);
        engine.getViewModel().updateAspect(mockFrame);
        engine.onOffsetsChanged(0.5f, 0, 0.1f, 0, -500, 0);
        Assert.assertNotEquals(0, engine.getViewModel().getOffset());
        config.orientation = Configuration.ORIENTATION_LANDSCAPE;
        engine.onOffsetsChanged(0.5f, 0, 0, 0, -500, 0);
        Assert.assertEquals(0, engine.getViewModel().getOffset());
    }

    @Test
    public void testDrawFrame_drawingLogic() throws Exception {
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        doReturn(mockSurfaceHolder).when(engineSpy).getSurfaceHolder();
        engineSpy.onVisibilityChanged(true);
        engineSpy.onSurfaceChanged(mockSurfaceHolder, 0, 1080, 1920);
        verify(mockAnimationFactory, timeout(1000).atLeastOnce()).create(anyString(), any(Context.class), any());
        engineSpy.getViewModel().setAnimation(mockAnimation);
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        engineSpy.drawFrame();
        verify(mockSurfaceHolder).lockHardwareCanvas();
        verify(mockCanvas).drawBitmap(any(Bitmap.class), any(), any(Rect.class), any(Paint.class));
        verify(mockSurfaceHolder).unlockCanvasAndPost(mockCanvas);
    }

    @Test
    public void testDrawFrame_blurEffectWhenFillFrameIsTrue() throws Exception {
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        doReturn(mockSurfaceHolder).when(engineSpy).getSurfaceHolder();
        WallpaperViewModel vm = engineSpy.getViewModel();
        vm.onSurfaceChanged(1080, 1920);
        vm.setAnimation(mockAnimation);
        vm.setFillFrame(true);
        vm.setScalingFactor(100f);
        // Use reflection to ensure ViewModel state is correct for the branch
        Field aspectField = WallpaperViewModel.class.getDeclaredField("mAspect");
        aspectField.setAccessible(true);
        aspectField.set(vm, 20000); // 2x original width
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        engineSpy.drawFrame();
        verify(mockCanvas).drawRenderNode(any(RenderNode.class));
    }

    @Test
    public void testAnimationClose_onDestroy() throws Exception {
        engine.onVisibilityChanged(true);
        getSettingsFromEngine(engine).race = "urquan";
        engine.onSettingsChanged(SettingsFragment.ALIEN_RACE);
        verify(mockAnimationFactory, timeout(1000).atLeastOnce()).create(anyString(), any(Context.class), any());
        engine.getViewModel().setAnimation(mockAnimation);
        engine.onDestroy();
        verify(mockAnimation, atLeastOnce()).close();
    }

    @Test
    public void testSettingsChanged_AlienRace() throws Exception {
        engine.onVisibilityChanged(true);
        String newRace = createString();
        when(mockAnimationFactory.create(eq(newRace), any(Context.class), any())).thenReturn(mockAnimation);
        getSettingsFromEngine(engine).race = newRace;
        engine.onSettingsChanged(SettingsFragment.ALIEN_RACE);
        verify(mockAnimationFactory, timeout(1000).atLeastOnce()).create(eq(newRace), any(Context.class), any());
    }

    @Test
    public void testDrawFrame_showsErrorWhenAnimationIsNull() throws Exception {
        when(mockAnimationFactory.create(anyString(), any(Context.class), any()))
                .thenThrow(new RuntimeException("Simulated load failure"));
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        doReturn(mockSurfaceHolder).when(engineSpy).getSurfaceHolder();
        engineSpy.onVisibilityChanged(true);
        engineSpy.onSurfaceChanged(mockSurfaceHolder, 0, 1080, 1920);
        verify(mockAnimationFactory, timeout(1000).atLeastOnce()).create(anyString(), any(Context.class), any());
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        engineSpy.drawFrame();
        verify(mockCanvas, atLeastOnce()).translate(anyFloat(), anyFloat());
    }

    @Test
    public void testFillFrameParallax_centering() {
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        doReturn(mockSurfaceHolder).when(engineSpy).getSurfaceHolder();
        engineSpy.getViewModel().setFillFrame(true);
        engineSpy.getViewModel().setScalingFactor(0f);
        engineSpy.onSurfaceChanged(mockSurfaceHolder, 0, 1080, 1920);
        engineSpy.getViewModel().setAnimation(mockAnimation);
        engineSpy.getViewModel().updateAspect(mockFrame);
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        engineSpy.drawFrame();
    }

    @Test
    public void testExecutorShutdown_onDestroy() throws Exception {
        UQMWallpaper.CommsEngine testEngine = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        Field field = UQMWallpaper.CommsEngine.class.getDeclaredField("mLoaderExecutor");
        field.setAccessible(true);
        java.util.concurrent.ExecutorService executor = (java.util.concurrent.ExecutorService) field.get(testEngine);
        Assert.assertNotNull(executor);
        Assert.assertFalse(executor.isShutdown());
        testEngine.onDestroy();
        Assert.assertTrue(executor.isShutdown());
    }

    @Test
    public void testViewModelStopped_onDestroy() throws Exception {
        UQMWallpaper.CommsEngine testEngine = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        WallpaperViewModel vm = testEngine.getViewModel();
        Field field = WallpaperViewModel.class.getDeclaredField("mWorkerThread");
        field.setAccessible(true);
        android.os.HandlerThread thread = (android.os.HandlerThread) field.get(vm);
        Assert.assertNotNull(thread);
        Assert.assertTrue(thread.isAlive());
        testEngine.onDestroy();
        Assert.assertNull(field.get(vm));
    }

    @Test
    public void testStagedSettings_AdoptionAndCommit() throws Exception {
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        prefs.edit().clear().putString(SettingsFragment.ALIEN_RACE, "urquan").apply();
        // 1. Manually prepare a staged settings object in COMMITTED state
        WallpaperSettings staged = new WallpaperSettings(prefs);
        staged.race = "spathi";
        staged.scalingFactor = 50.0f;
        staged.offset = -500;
        staged.setTargetFlags(WallpaperManager.FLAG_SYSTEM);
        staged.setState(WallpaperSettings.State.COMMITTED);
        Field stagedSettingsField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedSettingsField.setAccessible(true);
        stagedSettingsField.set(null, staged);
        // 2. Create a Live engine (Triggers Adoption via onCreate -> checkAndAdoptSettings)
        UQMWallpaper.CommsEngine liveEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(false).when(liveEngine).isPreview(); // Force Live mode for adoption
        doReturn(WallpaperManager.FLAG_SYSTEM).when(liveEngine).getWallpaperFlagsSafe();
        liveEngine.onCreate(mockSurfaceHolder);
        // 3. Verify Adoption in ViewModel
        Assert.assertEquals(-500, liveEngine.getViewModel().getUserOffset());
        Assert.assertEquals(50.0f, liveEngine.getViewModel().getScalingFactor(), 0.1f);
        // 4. Verify Persistence in SharedPreferences
        Assert.assertEquals("spathi", prefs.getString(SettingsFragment.ALIEN_RACE, ""));
        Assert.assertEquals(-500, prefs.getInt(UQMWallpaper.OFFSET_PREF, 0));
        Assert.assertEquals(50.0f, prefs.getFloat(SettingsFragment.SCALING_FACTOR, 0.0f), 0.1f);
        // 5. Verify sStagedSettings is cleared after successful adoption
        Assert.assertNull(stagedSettingsField.get(null));
    }

    @Test
    public void testStagedSettings_AdoptionAndCommit_LockScreen() throws Exception {
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_LOCK, Context.MODE_PRIVATE);
        prefs.edit().clear().putString(SettingsFragment.ALIEN_RACE, "urquan").apply();
        // 1. Manually prepare a staged settings object in COMMITTED state for LOCK screen
        WallpaperSettings staged = new WallpaperSettings(prefs);
        staged.race = "slylandro";
        staged.setTargetFlags(WallpaperManager.FLAG_LOCK);
        staged.setState(WallpaperSettings.State.COMMITTED);
        Field stagedSettingsField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedSettingsField.setAccessible(true);
        stagedSettingsField.set(null, staged);
        // 2. Create a Live engine for LOCK screen
        UQMWallpaper.CommsEngine liveEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(false).when(liveEngine).isPreview();
        doReturn(WallpaperManager.FLAG_LOCK).when(liveEngine).getWallpaperFlagsSafe();
        liveEngine.onCreate(mockSurfaceHolder);
        // 3. Verify Adoption
        Assert.assertEquals("slylandro", liveEngine.getViewModel().getSettings().race);
        Assert.assertEquals("slylandro", prefs.getString(SettingsFragment.ALIEN_RACE, ""));
    }

    @Test
    public void testPreview_ClearsAbandonedSession() throws Exception {
        // 1. Setup a "stale" staged settings object
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        WallpaperSettings staleStaged = new WallpaperSettings(prefs);
        staleStaged.race = "spathi";
        staleStaged.setState(WallpaperSettings.State.STAGED);
        Field stagedSettingsField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedSettingsField.setAccessible(true);
        stagedSettingsField.set(null, staleStaged);
        // 2. Create a NEW preview engine.
        UQMWallpaper.CommsEngine newPreview = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(true).when(newPreview).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM).when(newPreview).getWallpaperFlagsSafe();
        // 3. Call onCreate. This should detect the abandoned session and clear staleStaged.
        newPreview.onCreate(mockSurfaceHolder);
        // 4. Verify that the engine got NEW settings (cloned from live) and not the stale ones.
        WallpaperSettings currentSettings = getSettingsFromEngine(newPreview);
        Assert.assertNotEquals("spathi", currentSettings.race);
        Assert.assertEquals("urquan", currentSettings.race); // Live default
    }

    @Test
    public void testOnSettingsChanged_EdgeCases() {
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        engineSpy.onSettingsChanged(null);
        verify(engineSpy, never()).getViewModel();
        engineSpy.onSettingsChanged("unknown_key");
        engineSpy.onSettingsChanged(SettingsFragment.ALIEN_RACE);
    }

    @Test
    public void testOnSettingsChanged_OtherKeys() {
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        WallpaperViewModel vm = engineSpy.getViewModel();
        WallpaperSettings settings = getSettingsFromEngine(engineSpy);
        settings.scalingFactor = 75.0f;
        engineSpy.onSettingsChanged(SettingsFragment.SCALING_FACTOR);
        Assert.assertEquals(75.0f, vm.getScalingFactor(), 0.1f);
        settings.fillFrame = true;
        engineSpy.onSettingsChanged(SettingsFragment.FILL_FRAME);
        Assert.assertTrue(vm.getFillFrame());
        settings.offset = -123;
        engineSpy.onSettingsChanged(UQMWallpaper.OFFSET_PREF);
        Assert.assertEquals(-123, vm.getUserOffset());
    }

    @Test
    public void testPreview_ResumesExistingSession() throws Exception {
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        WallpaperSettings existingStaged = new WallpaperSettings(prefs);
        String randomRace = createString();
        existingStaged.race = randomRace;
        existingStaged.setState(WallpaperSettings.State.STAGED);
        Field field = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        field.setAccessible(true);
        field.set(null, existingStaged);
        // Simulate another active preview engine to prevent clearing
        UQMWallpaper.CommsEngine otherPreview = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        // Use reflection to set mCreated and mIsPreview to true on the real object in the list
        Field createdField = UQMWallpaper.CommsEngine.class.getDeclaredField("mCreated");
        createdField.setAccessible(true);
        createdField.set(otherPreview, true);
        Field isPreviewField = UQMWallpaper.CommsEngine.class.getDeclaredField("mIsPreview");
        isPreviewField.setAccessible(true);
        isPreviewField.set(otherPreview, true);
        UQMWallpaper.CommsEngine previewEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(true).when(previewEngine).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM).when(previewEngine).getWallpaperFlagsSafe();
        previewEngine.onCreate(mockSurfaceHolder);
        Assert.assertEquals(randomRace, getSettingsFromEngine(previewEngine).race);
    }

    @Test
    public void testPerformAdoption_CommitFailure() throws Exception {
        UQMWallpaper spyService = spy(wallpaperService);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockEditor.commit()).thenReturn(false);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.putFloat(anyString(), anyFloat())).thenReturn(mockEditor);
        when(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor);
        when(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor);
        SharedPreferences mockPrefs = mock(SharedPreferences.class);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        doReturn(mockPrefs).when(spyService).getSharedPreferences(eq(UQMWallpaper.PREFS_HOME), anyInt());
        // Create engine from spyService so its this$0 is correctly set
        UQMWallpaper.CommsEngine testEngine = spy((UQMWallpaper.CommsEngine) spyService.onCreateEngine());
        doReturn(false).when(testEngine).isPreview(); // Force live mode for adoption
        doReturn(WallpaperManager.FLAG_SYSTEM).when(testEngine).getWallpaperFlagsSafe();
        // Setup staged settings
        WallpaperSettings staged = new WallpaperSettings(wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE));
        staged.setTargetFlags(WallpaperManager.FLAG_SYSTEM);
        staged.setState(WallpaperSettings.State.STAGED);
        Field stagedSettingsField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedSettingsField.setAccessible(true);
        stagedSettingsField.set(null, staged);
        testEngine.onCommand("android.wallpaper.reapply", 0, 0, 0, null, false);
        verify(mockEditor).commit();
        // sStagedSettings should NOT be cleared if commit fails.
        Assert.assertNotNull(stagedSettingsField.get(null));
    }

    @Test
    public void testDrawFrame_StatusMessageInPreview() throws Exception {
        UQMWallpaper.CommsEngine previewEngine = spy(engine);
        // Use reflection to set mIsPreview
        Field f = UQMWallpaper.CommsEngine.class.getDeclaredField("mIsPreview");
        f.setAccessible(true);
        f.set(previewEngine, true);
        doReturn(mockSurfaceHolder).when(previewEngine).getSurfaceHolder();
        WallpaperViewModel vm = previewEngine.getViewModel();
        vm.onSurfaceChanged(1080, 1920);
        vm.setAnimation(mockAnimation);
        vm.setScalingFactor(100f);
        vm.setTotalWidth(2000); // Ensure scaledWidth > surface width
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        previewEngine.drawFrame();
        // Verify drawStatusMessage was called via translate/draw
        verify(mockCanvas, atLeastOnce()).translate(anyFloat(), anyFloat());
    }

    @Test
    public void testOnDesiredSizeChanged_UpdatesTotalWidth() {
        int newDesiredWidth = rand.nextInt(3000);
        int newDesiredHeight = rand.nextInt(2000);
        engine.onDesiredSizeChanged(newDesiredWidth, newDesiredHeight);
        Assert.assertEquals(newDesiredWidth, engine.getViewModel().getTotalWidth());
    }

    @Test
    public void testOnSurfaceChanged_WithIntermediateWidth_SchedulesRetry() throws Exception {
        // Setup: Shadow manager returns same width as surface (intermediate)
        int screenWidth = rand.nextInt(1080);
        int finalWidth = rand.nextInt(2160);
        java.lang.reflect.Method applyMethod = UQMWallpaper.CommsEngine.class.getDeclaredMethod("applyNewTotalWidth", int.class, int.class);
        applyMethod.setAccessible(true);
        applyMethod.invoke(engine, screenWidth, screenWidth);
        Assert.assertEquals(screenWidth, engine.getViewModel().getTotalWidth());
        wallpaperService.setTotalWidth(finalWidth);
        ShadowLooper.idleMainLooper(501, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testApplyNewTotalWidth_IgnoresInvalidWidth() throws Exception {
        int initialWidth = engine.getViewModel().getTotalWidth();
        java.lang.reflect.Method applyMethod = UQMWallpaper.CommsEngine.class.getDeclaredMethod("applyNewTotalWidth", int.class, int.class);
        applyMethod.setAccessible(true);
        // Call with invalid width (0 or negative)
        applyMethod.invoke(engine, 0, 0);
        // Should remain unchanged
        Assert.assertEquals(initialWidth, engine.getViewModel().getTotalWidth());
    }

    @Test
    public void testPerformAdoption_UpdatesAllEngines() throws Exception {
        UQMWallpaper.CommsEngine engine1 = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        UQMWallpaper.CommsEngine engine2 = (UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine();
        // Manually set different settings for engine2 to force the branch
        Field field = UQMWallpaper.CommsEngine.class.getDeclaredField("mSettings");
        field.setAccessible(true);
        WallpaperSettings oldSettings = mock(WallpaperSettings.class);
        field.set(engine2, oldSettings);
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        WallpaperSettings staged = new WallpaperSettings(prefs);
        staged.setTargetFlags(WallpaperManager.FLAG_SYSTEM);
        staged.setState(WallpaperSettings.State.COMMITTED);
        Field stagedField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedField.setAccessible(true);
        stagedField.set(null, staged);
        engine1.onCommand("android.wallpaper.reapply", 0, 0, 0, null, false);
        verify(oldSettings).removeListener(engine2);
        Assert.assertSame(getSettingsFromEngine(engine2), UQMWallpaper.getLiveSettings());
    }

    @Test
    public void testOnTouchEvent_LiveMode_Ignored() {
        UQMWallpaper.CommsEngine liveEngine = spy(engine);
        doReturn(false).when(liveEngine).isPreview();
        MotionEvent event = mock(MotionEvent.class);
        liveEngine.onTouchEvent(event);
        verify(event, never()).getActionMasked();
    }

    @Test
    public void testOnTouchEvent_PreviewMode_DelegatesToViewModel() throws Exception {
        UQMWallpaper.CommsEngine previewEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(true).when(previewEngine).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM).when(previewEngine).getWallpaperFlagsSafe();
        previewEngine.onCreate(mockSurfaceHolder);
        WallpaperViewModel vm = previewEngine.getViewModel();
        vm.setTotalWidth(2000);
        vm.setScalingFactor(0f);
        vm.onSurfaceChanged(1000, 1000);
        vm.updateAspect(mockFrame); // Frame is 640x480
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 500, 500, 0);
        previewEngine.onTouchEvent(event);
        WallpaperSettings settings = getSettingsFromEngine(previewEngine);
        Assert.assertEquals(vm.getUserOffset(), settings.offset);
        Assert.assertEquals(vm.getScalingFactor(), settings.scalingFactor, 0.1f);
    }

    @Test
    public void testLoadAnimation_ErrorLoadingAlien() throws Exception {
        String race = createString();
        when(mockAnimationFactory.create(eq(race), any(Context.class), any())).thenThrow(new RuntimeException("Generic load error"));
        WallpaperSettings settings = getSettingsFromEngine(engine);
        settings.race = race;
        engine.onSettingsChanged(SettingsFragment.ALIEN_RACE);
        engine.onVisibilityChanged(true);
        verify(mockAnimationFactory, timeout(1000).atLeastOnce()).create(eq(race), any(Context.class), any());
        Assert.assertEquals(wallpaperService.getString(R.string.error_loading_alien, race), engine.getViewModel().getErrorMessage());
    }

    @Test
    public void testDrawFrame_FillFrameParallax() throws Exception {
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        doReturn(mockSurfaceHolder).when(engineSpy).getSurfaceHolder();
        WallpaperViewModel vm = engineSpy.getViewModel();
        vm.onSurfaceChanged(1080, 1920);
        vm.setAnimation(mockAnimation);
        vm.setFillFrame(true);
        vm.setScalingFactor(100f);
        Field aspectField = WallpaperViewModel.class.getDeclaredField("mAspect");
        aspectField.setAccessible(true);
        aspectField.set(vm, 20000); // 2x original width
        engineSpy.onOffsetsChanged(0.5f, 0, 0.1f, 0, 0, 0);
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        engineSpy.drawFrame();
        verify(mockCanvas).drawRenderNode(any(RenderNode.class));
    }

    @Test
    public void testOnSurfaceChanged_ZeroDimensions_Ignored() {
        engine.onSurfaceChanged(mockSurfaceHolder, 0, 0, 0);
        Assert.assertEquals(0, engine.getViewModel().getWidth());
    }

    @Test
    public void testOnSettingsChanged_ExceptionHandler() throws Exception {
        WallpaperViewModel spyVM = spy(engine.getViewModel());
        doThrow(new RuntimeException("Forced exception")).when(spyVM).setScalingFactor(anyFloat());
        Field field = UQMWallpaper.CommsEngine.class.getDeclaredField("mViewModel");
        field.setAccessible(true);
        field.set(engine, spyVM);
        engine.onSettingsChanged(SettingsFragment.SCALING_FACTOR);
        Assert.assertNull(engine.getViewModel().getAnimation());
    }

    @Test
    public void testOnSurfaceChanged_AnimationExists() throws Exception {
        engine.getViewModel().setAnimation(mockAnimation);
        engine.onSurfaceChanged(mockSurfaceHolder, 0, 1080, 1920);
        verify(mockAnimation, atLeastOnce()).getFrame();
    }

    @Test
    public void testOnCommand_REAPPLY_MissingTargetFlags() throws Exception {
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        WallpaperSettings staged = new WallpaperSettings(prefs);
        staged.setTargetFlags(0);
        staged.setState(WallpaperSettings.State.STAGED);
        Field field = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        field.setAccessible(true);
        field.set(null, staged);
        engine.onCommand("android.wallpaper.reapply", 0, 0, 0, null, false);
        Assert.assertEquals(WallpaperSettings.State.COMMITTED, staged.getState());
    }

    @Test
    public void testPerformAdoption_NoStagedSettings() throws Exception {
        Field field = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        field.setAccessible(true);
        field.set(null, null);
        java.lang.reflect.Method method = UQMWallpaper.CommsEngine.class.getDeclaredMethod("performAdoption");
        method.setAccessible(true);
        method.invoke(engine);
        // Should return normally
    }

    @Test
    public void testOnSurfaceChanged_IntermediateWidth_SurfaceMismatch() throws Exception {
        java.lang.reflect.Method applyMethod = UQMWallpaper.CommsEngine.class.getDeclaredMethod("applyNewTotalWidth", int.class, int.class);
        applyMethod.setAccessible(true);
        applyMethod.invoke(engine, 3000, 1080);
        Assert.assertEquals(3000, engine.getViewModel().getTotalWidth());
    }

    @Test
    public void testPreview_AbandonedSession_Committed() throws Exception {
        String race = createString();
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        WallpaperSettings staleStaged = new WallpaperSettings(prefs);
        staleStaged.race = race;
        staleStaged.setState(WallpaperSettings.State.COMMITTED);
        Field stagedSettingsField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedSettingsField.setAccessible(true);
        stagedSettingsField.set(null, staleStaged);
        UQMWallpaper.CommsEngine newPreview = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(true).when(newPreview).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM).when(newPreview).getWallpaperFlagsSafe();
        // This should NOT clear staleStaged because it's COMMITTED (waiting for adoption)
        newPreview.onCreate(mockSurfaceHolder);
        WallpaperSettings currentSettings = getSettingsFromEngine(newPreview);
        Assert.assertEquals(race, currentSettings.race);
    }

    @Test
    public void testOnVisibilityChanged_updatesViewModel() {
        engine.onVisibilityChanged(true);
        Assert.assertTrue(engine.getViewModel().isVisible());
        engine.onVisibilityChanged(false);
        Assert.assertFalse(engine.getViewModel().isVisible());
    }

    @Test
    public void testLiveEngine_TriggersBatonPass_FromStagedToCommitted() throws Exception {
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        WallpaperSettings staged = new WallpaperSettings(prefs);
        staged.setState(WallpaperSettings.State.STAGED);
        Field stagedSettingsField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedSettingsField.setAccessible(true);
        stagedSettingsField.set(null, staged);
        UQMWallpaper.CommsEngine liveEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(false).when(liveEngine).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM).when(liveEngine).getWallpaperFlagsSafe();
        liveEngine.onCreate(mockSurfaceHolder);
        Assert.assertEquals(WallpaperSettings.State.COMMITTED, staged.getState());
    }

    @Test
    public void testCheckAndAdoptSettings_LogsWhenNothingReady() throws Exception {
        Field field = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        field.setAccessible(true);
        field.set(null, null);
        UQMWallpaper.CommsEngine liveEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(false).when(liveEngine).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM).when(liveEngine).getWallpaperFlagsSafe();
        liveEngine.onCreate(mockSurfaceHolder);
    }

    @Test
    public void testDeferredInitialization_LoadsOnlyWhenVisible() throws Exception {
        Mockito.reset(mockAnimationFactory);
        UQMWallpaper.CommsEngine testEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(false).when(testEngine).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM).when(testEngine).getWallpaperFlagsSafe();
        testEngine.onCreate(mockSurfaceHolder);
        testEngine.onSurfaceChanged(mockSurfaceHolder, 0, 1080, 1920);
        verify(mockAnimationFactory, never()).create(anyString(), any(Context.class), any());
        testEngine.onVisibilityChanged(true);
        verify(mockAnimationFactory, timeout(1000).atLeastOnce()).create(anyString(), any(Context.class), any());
    }

    @Test
    public void testOnSettingsChanged_Race_DeferredWhenHidden() throws Exception {
        String race = createString();
        engine.onVisibilityChanged(false);
        Mockito.reset(mockAnimationFactory);
        getSettingsFromEngine(engine).race = race;
        engine.onSettingsChanged(SettingsFragment.ALIEN_RACE);
        // Verify NOT loaded
        verify(mockAnimationFactory, never()).create(anyString(), any(Context.class), any());
        Assert.assertNull(engine.getViewModel().getAnimation());
        // Now set visible
        engine.onVisibilityChanged(true);
        // Verify loaded
        verify(mockAnimationFactory, timeout(1000).atLeastOnce()).create(eq(race), any(Context.class), any());
    }

    @Test
    public void testDrawFrame_LoadingState() throws Exception {
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        doReturn(mockSurfaceHolder).when(engineSpy).getSurfaceHolder();
        WallpaperViewModel vm = engineSpy.getViewModel();
        vm.setAnimation(null);
        vm.setLoading(true);
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        engineSpy.drawFrame();
        // Should draw status message (translate will be called)
        verify(mockCanvas, atLeastOnce()).translate(anyFloat(), anyFloat());
        // Verify we didn't try to draw a bitmap
        verify(mockCanvas, never()).drawBitmap(any(Bitmap.class), any(), any(Rect.class), any(Paint.class));
    }

    @Test
    public void testDrawFrame_AdaptiveHints_PinchOnly() throws Exception {
        int width = rand.nextInt(0xFFFF);
        int height = rand.nextInt(0xFFFF);
        UQMWallpaper.CommsEngine previewEngine = spy(engine);
        Field f = UQMWallpaper.CommsEngine.class.getDeclaredField("mIsPreview");
        f.setAccessible(true);
        f.set(previewEngine, true);
        doReturn(mockSurfaceHolder).when(previewEngine).getSurfaceHolder();
        WallpaperViewModel vm = previewEngine.getViewModel();
        vm.onSurfaceChanged(width, height);
        vm.setAnimation(mockAnimation);
        vm.setScalingFactor(0f); // Fits on a single screen
        vm.setTotalWidth(width);
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        previewEngine.drawFrame();
        verify(mockCanvas, atLeastOnce()).translate(anyFloat(), anyFloat());
    }

    @Test
    public void testDrawFrame_AdaptiveHints_DragToCenter() throws Exception {
        UQMWallpaper.CommsEngine previewEngine = spy(engine);
        Field f = UQMWallpaper.CommsEngine.class.getDeclaredField("mIsPreview");
        f.setAccessible(true);
        f.set(previewEngine, true);
        doReturn(mockSurfaceHolder).when(previewEngine).getSurfaceHolder();
        WallpaperViewModel vm = previewEngine.getViewModel();
        vm.onSurfaceChanged(rand.nextInt(0xFFFF), rand.nextInt(0xFFFF));
        vm.setAnimation(mockAnimation); // 640x480
        vm.setScalingFactor(rand.nextFloat(100f));
        vm.setTotalWidth(rand.nextInt(0xFFFF));
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        previewEngine.drawFrame();
        verify(mockCanvas, atLeastOnce()).translate(anyFloat(), anyFloat());
    }

    @Test
    public void testDrawFrame_NoStatusMessageInLiveMode() throws Exception {
        UQMWallpaper.CommsEngine liveEngine = spy(engine);
        doReturn(false).when(liveEngine).isPreview();
        doReturn(mockSurfaceHolder).when(liveEngine).getSurfaceHolder();
        WallpaperViewModel vm = liveEngine.getViewModel();
        vm.onSurfaceChanged(rand.nextInt(0xFFFF), rand.nextInt(0xFFFF));
        vm.setAnimation(mockAnimation);
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        liveEngine.drawFrame();
        // LIVE mode with animation should NOT call translate for messages
        verify(mockCanvas, never()).translate(anyFloat(), anyFloat());
    }

    @Test
    public void testDrawFrame_NoLoadingMessageWhenNotLoading() throws Exception {
        UQMWallpaper.CommsEngine engineSpy = spy(engine);
        doReturn(mockSurfaceHolder).when(engineSpy).getSurfaceHolder();
        WallpaperViewModel vm = engineSpy.getViewModel();
        vm.setAnimation(null);
        vm.setLoading(false);
        vm.setErrorMessage(null);
        vm.onSurfaceChanged(rand.nextInt(0xFFFF), rand.nextInt(0xFFFF));
        Mockito.reset(mockSurfaceHolder, mockCanvas, mockSurface);
        when(mockSurfaceHolder.getSurface()).thenReturn(mockSurface);
        when(mockSurface.isValid()).thenReturn(true);
        when(mockSurfaceHolder.lockHardwareCanvas()).thenReturn(mockCanvas);
        engineSpy.drawFrame();
        // No error, no loading -> no message
        verify(mockCanvas, never()).translate(anyFloat(), anyFloat());
    }

    @Test
    public void testLoadAnimation_RejectedExecutionException() throws Exception {
        Field executorField = UQMWallpaper.CommsEngine.class.getDeclaredField("mLoaderExecutor");
        executorField.setAccessible(true);
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.isShutdown()).thenReturn(false);
        doThrow(new RejectedExecutionException()).when(mockExecutor).execute(any(Runnable.class));
        executorField.set(engine, mockExecutor);
        engine.onVisibilityChanged(true);
        getSettingsFromEngine(engine).race = createString();
        engine.onSettingsChanged(SettingsFragment.ALIEN_RACE);
        Assert.assertFalse(engine.getViewModel().isLoading());
    }

    @Test
    public void testOnSurfaceChanged_NegativeDimensions_Ignored() {
        int format = rand.nextInt(0xFFFF);
        int width = rand.nextInt(0xFFFF);
        int height = rand.nextInt(0xFFFF);
        engine.onSurfaceChanged(mockSurfaceHolder, format, -width, height);
        Assert.assertEquals(0, engine.getViewModel().getWidth());
        engine.onSurfaceChanged(mockSurfaceHolder, format, width, -height);
        Assert.assertEquals(0, engine.getViewModel().getHeight());
    }

    @Test
    public void testTransientVisibility_AbortsLoading() throws Exception {
        CountDownLatch factoryLatch = new CountDownLatch(1);
        CountDownLatch visibilityLatch = new CountDownLatch(1);
        when(mockAnimationFactory.create(anyString(), any(Context.class), any())).thenAnswer(invocation -> {
            factoryLatch.countDown();
            visibilityLatch.await(2, TimeUnit.SECONDS);
            return mockAnimation;
        });
        engine.onVisibilityChanged(true);
        Assert.assertTrue("Factory should be entered", factoryLatch.await(1, TimeUnit.SECONDS));
        engine.onVisibilityChanged(false);
        visibilityLatch.countDown();
        verify(mockAnimation, timeout(1000)).close();
        ShadowLooper.idleMainLooper();
        Assert.assertNull("Animation should be null in ViewModel", engine.getViewModel().getAnimation());
    }

    @Test
    public void testOnVisibilityChanged_ReleasesResourcesWhenHidden() throws Exception {
        engine.onVisibilityChanged(true);
        verify(mockAnimationFactory, timeout(1000)).create(anyString(), any(Context.class), any());
        // Ensure ViewModel has the animation
        long start = System.currentTimeMillis();
        while (engine.getViewModel().getAnimation() == null && System.currentTimeMillis() - start < 2000) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        Assert.assertNotNull("Animation should be loaded", engine.getViewModel().getAnimation());
        engine.onVisibilityChanged(false);
        idleWorker();
        Assert.assertNull("Animation should be null in ViewModel", engine.getViewModel().getAnimation());
        verify(mockAnimation, timeout(1000)).close();
    }

    @Test
    public void testLoadAnimation_Canceled() throws Exception {
        String race = "test_race";
        when(mockAnimationFactory.create(eq(race), any(Context.class), any())).thenThrow(new OperationCanceledException());
        engine.onVisibilityChanged(true);
        getSettingsFromEngine(engine).race = race;
        engine.onSettingsChanged(SettingsFragment.ALIEN_RACE);
        ShadowLooper.idleMainLooper(1000, TimeUnit.MILLISECONDS);
        // Verify that we didn't log an error or set an error message
        Assert.assertNull(engine.getViewModel().getErrorMessage());
        Assert.assertFalse(engine.getViewModel().isLoading());
        // Verify the cancellation log message was produced
        mockedStaticLog.verify(() -> Log.i(eq(UQMWallpaper.TAG), anyString()), atLeastOnce());
    }

    @Test
    public void testGetWallpaperFlagsSafe_CatchBlock() {
        UQMWallpaper.CommsEngine spyEngine = spy(engine);
        // Robolectric's getWallpaperFlags() might already throw NPE if not fully mocked,
        // but let's be explicit.
        doThrow(new NullPointerException()).when(spyEngine).getWallpaperFlags();
        int flags = spyEngine.getWallpaperFlagsSafe();
        Assert.assertEquals(WallpaperManager.FLAG_SYSTEM, flags);
    }

    @Test
    public void testMigrateToNamespacedPrefs() throws Exception {
        SharedPreferences defaultPrefs = mock(SharedPreferences.class);
        SharedPreferences homePrefs = mock(SharedPreferences.class);
        SharedPreferences lockPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor defaultEditor = mock(SharedPreferences.Editor.class);
        SharedPreferences.Editor homeEditor = mock(SharedPreferences.Editor.class);
        SharedPreferences.Editor lockEditor = mock(SharedPreferences.Editor.class);
        Map<String, Object> defaultContent = new HashMap<>();
        defaultContent.put("race", createString());
        defaultContent.put("scaling", rand.nextFloat() * 100.0f);
        defaultContent.put("offset", rand.nextInt() * -1);
        defaultContent.put("fill", rand.nextBoolean());
        defaultContent.put("long", rand.nextLong());
        defaultContent.put("bool", rand.nextBoolean());
        when(homePrefs.getAll()).thenReturn(new HashMap<>());
        doReturn(defaultContent).when(defaultPrefs).getAll();
        when(defaultPrefs.edit()).thenReturn(defaultEditor);
        when(homePrefs.edit()).thenReturn(homeEditor);
        when(lockPrefs.edit()).thenReturn(lockEditor);
        when(homeEditor.putString(anyString(), anyString())).thenReturn(homeEditor);
        when(homeEditor.putFloat(anyString(), anyFloat())).thenReturn(homeEditor);
        when(homeEditor.putInt(anyString(), anyInt())).thenReturn(homeEditor);
        when(homeEditor.putBoolean(anyString(), anyBoolean())).thenReturn(homeEditor);
        when(homeEditor.putLong(anyString(), anyLong())).thenReturn(homeEditor);
        when(lockEditor.putString(anyString(), anyString())).thenReturn(lockEditor);
        when(lockEditor.putFloat(anyString(), anyFloat())).thenReturn(lockEditor);
        when(lockEditor.putInt(anyString(), anyInt())).thenReturn(lockEditor);
        when(lockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(lockEditor);
        when(lockEditor.putLong(anyString(), anyLong())).thenReturn(lockEditor);
        when(defaultEditor.clear()).thenReturn(defaultEditor);
        java.lang.reflect.Method method = UQMWallpaper.class.getDeclaredMethod("migrateToNamespacedPrefs", SharedPreferences.class);
        method.setAccessible(true);
        UQMWallpaper spyService = spy(wallpaperService);
        doReturn(homePrefs).when(spyService).getSharedPreferences(eq(UQMWallpaper.PREFS_HOME), anyInt());
        doReturn(lockPrefs).when(spyService).getSharedPreferences(eq(UQMWallpaper.PREFS_LOCK), anyInt());
        method.invoke(spyService, defaultPrefs);
        verify(homeEditor).apply();
        verify(lockEditor).apply();
        verify(defaultEditor).apply();
    }

    @Test
    public void testBothScenario_ExpandsTargetsCorrectly() throws Exception {
        String race = createString();
        UQMWallpaper.CommsEngine previewEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(true).when(previewEngine).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM).when(previewEngine).getWallpaperFlagsSafe();
        previewEngine.onCreate(mockSurfaceHolder);
        Field stagedField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedField.setAccessible(true);
        WallpaperSettings staged = (WallpaperSettings) stagedField.get(null);
        Assert.assertNotNull(staged);
        Assert.assertEquals(WallpaperManager.FLAG_SYSTEM, staged.getTargetFlags());
        staged.race = race;
        UQMWallpaper.CommsEngine liveBothEngine = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(false).when(liveBothEngine).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK).when(liveBothEngine).getWallpaperFlagsSafe();
        liveBothEngine.onCreate(mockSurfaceHolder);
        SharedPreferences homePrefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        SharedPreferences lockPrefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_LOCK, Context.MODE_PRIVATE);
        Assert.assertEquals(race, homePrefs.getString(SettingsFragment.ALIEN_RACE, ""));
        Assert.assertEquals(race, lockPrefs.getString(SettingsFragment.ALIEN_RACE, ""));
        Assert.assertNull(stagedField.get(null));
    }

    @Test
    public void testReapplyCommand_UpdatesTargets() throws Exception {
        String race = createString();
        WallpaperSettings staged = new WallpaperSettings(wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE));
        staged.setTargetFlags(WallpaperManager.FLAG_SYSTEM);
        staged.setState(WallpaperSettings.State.STAGED);
        staged.race = race;
        Field stagedField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedField.setAccessible(true);
        stagedField.set(null, staged);
        UQMWallpaper.CommsEngine engineSpy = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(false).when(engineSpy).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK).when(engineSpy).getWallpaperFlagsSafe();
        engineSpy.onCreate(mockSurfaceHolder);
        engineSpy.onCommand("android.wallpaper.reapply", 0, 0, 0, null, false);
        SharedPreferences homePrefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        SharedPreferences lockPrefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_LOCK, Context.MODE_PRIVATE);
        Assert.assertEquals(race, homePrefs.getString(SettingsFragment.ALIEN_RACE, ""));
        Assert.assertEquals(race, lockPrefs.getString(SettingsFragment.ALIEN_RACE, ""));
    }

    @Test
    public void testCheckAndAdoptSettings_ExpansionBranchCoverage() throws Exception {
        SharedPreferences prefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_HOME, Context.MODE_PRIVATE);
        WallpaperSettings staged = new WallpaperSettings(prefs);
        staged.setTargetFlags(WallpaperManager.FLAG_SYSTEM);
        staged.setState(WallpaperSettings.State.COMMITTED);
        String testRace = createString();
        staged.race = testRace;
        Field stagedField = UQMWallpaper.class.getDeclaredField("sStagedSettings");
        stagedField.setAccessible(true);
        stagedField.set(null, staged);
        UQMWallpaper.CommsEngine engineSpy = spy((UQMWallpaper.CommsEngine) wallpaperService.onCreateEngine());
        doReturn(false).when(engineSpy).isPreview();
        doReturn(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK).when(engineSpy).getWallpaperFlagsSafe();
        Field flagsField = UQMWallpaper.CommsEngine.class.getDeclaredField("mWallpaperFlags");
        flagsField.setAccessible(true);
        flagsField.set(engineSpy, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
        java.lang.reflect.Method method = UQMWallpaper.CommsEngine.class.getDeclaredMethod("checkAndAdoptSettings", String.class);
        method.setAccessible(true);
        method.invoke(engineSpy, "test_trigger");
        SharedPreferences lockPrefs = wallpaperService.getSharedPreferences(UQMWallpaper.PREFS_LOCK, Context.MODE_PRIVATE);
        Assert.assertEquals(testRace, lockPrefs.getString(SettingsFragment.ALIEN_RACE, ""));
    }

    private void idleWorker() {
        try {
            Field field = WallpaperViewModel.class.getDeclaredField("mWorkerThread");
            field.setAccessible(true);
            android.os.HandlerThread thread = (android.os.HandlerThread) field.get(engine.getViewModel());
            if (thread != null) {
                ShadowLooper shadowLooper = org.robolectric.Shadows.shadowOf(thread.getLooper());
                shadowLooper.idle();
            }
        } catch (Exception ignored) {}
    }

    private WallpaperSettings getSettingsFromEngine(UQMWallpaper.CommsEngine engine) {
        try {
            Field field = UQMWallpaper.CommsEngine.class.getDeclaredField("mSettings");
            field.setAccessible(true);
            return (WallpaperSettings) field.get(engine);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
