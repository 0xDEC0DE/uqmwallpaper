package net.submedia.android.uqmlivewallpaper;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AnimationTest extends BaseTest {

    private MockedStatic<SystemClock> mockedStaticSystemClock;
    private ContentFixture T;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mockedStaticSystemClock = mockStatic(SystemClock.class);
        mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(0L);

        when(mockContext.getResources()).thenReturn(mockResources);

        T = new ContentFixture();
        // Setup Content injection
        Animation.setContentFactory((races, context, cancelled) -> T.build(this));
    }

    @After
    public void tearDown() throws Exception {
        Animation.setContentFactory(Content::new);
        if (mockedStaticSystemClock != null) {
            mockedStaticSystemClock.close();
            mockedStaticSystemClock = null;
        }
        T = null;
        super.tearDown();
    }

    /**
     * Helper to create a valid animation frame definition with controlled parameters.
     * Constrains random rates to sane ranges (1-100ms) to avoid test flakiness.
     */
    private int[] createFrameDef(int startIndex, int numFrames, int animFlags, int blockMask) {
        return new int[]{
                startIndex,
                numFrames,
                animFlags,
                rand.nextInt(1, 50),   // BaseFrameRate
                rand.nextInt(1, 50),   // RandomFrameRate
                rand.nextInt(1, 50),   // BaseRestartRate
                rand.nextInt(1, 50),   // RandomRestartRate
                blockMask
        };
    }

    private int[] createFrameDef() {
        final int[] anims = {Animation.RANDOM_ANIM, Animation.YOYO_ANIM, Animation.CIRCULAR_ANIM};
        return createFrameDef(
                rand.nextInt(10),
                rand.nextInt(2, 20), // Use at least 2 frames to avoid immediate wrap-around in basic tests
                anims[rand.nextInt(anims.length)],
                0
        );
    }

    /* ------------------------------------------------------------------------
     * Animation Tests
     * -----------------------------------------------------------------------*/
    @Test
    public void testAnimation() throws Exception {
        T.setup(this);
        String alienRace = T.params.alienRace();
        String pngFilename = T.params.pngFilename();

        int[] frameValues = createFrameDef();
        animationMocksHelper(alienRace, frameValues);

        try (Animation animation = new Animation(alienRace, mockContext, () -> false)) {
            Assert.assertNotNull(animation);
            Assert.assertNotNull(animation.getContent());
            Assert.assertFalse(animation.getContent().frame.isEmpty());
            String expectedPath = "comm/%s/%s".formatted(alienRace, pngFilename);
            Assert.assertEquals(expectedPath, animation.getContent().frame.get(0).filename);

            Animation.Frame frame = animation.getFrameList().get(0);
            Assert.assertEquals(frameValues[3], frame.BaseFrameRate);
        }
    }

    @Test
    public void testAnimation_nullAlienRace() {
        Exception thrown = Assert.assertThrows(Exception.class, () -> new Animation(null, mockContext, () -> false));
        Assert.assertEquals("no alien_race passed", thrown.getMessage());
    }

    @Test
    public void testAnimation_alienRaceResIdNotFound() {
        String alienRace = createString();
        when(mockContext.getPackageName()).thenReturn(createString());
        when(mockResources.getIdentifier(alienRace, "array", mockContext.getPackageName())).thenReturn(0);

        Exception thrown = Assert.assertThrows(Exception.class, () -> new Animation(alienRace, mockContext, () -> false));
        Assert.assertEquals("Could not find resource id for " + alienRace, thrown.getMessage());
    }

    @Test
    public void testToString() throws Exception {
        Content content = T.buildContent(this);

        int[] vals = createFrameDef();
        try (Animation animation = new Animation(content, List.of(vals), null)) {
            String expected = "Start[%05d] Frames[%02d] Flags[%02d] FrameRate[%05d] FrameRate2[%05d] Restart[%05d] Restart2[%05d] Block[%010d]%n";
            Assert.assertEquals(
                    expected.formatted(Arrays.stream(vals).boxed().toArray()) + content,
                    animation.toString()
            );
        }
    }

    @Test
    public void testGetFrame_initialAlarmSkip() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int initialAlarm = rand.nextInt(500) + 100;
        int elapsedTicks = rand.nextInt(initialAlarm - Animation.FRAME_RATE - 1);

        int[] frameValues = createFrameDef(0, 2, Animation.CIRCULAR_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(
                2,
                frameValues,
                canvasRef,
                initialAlarm
        )) {
            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn((long) elapsedTicks);
            animation.getFrame();

            verify(canvasRef.get(), times(0)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertEquals(initialAlarm - elapsedTicks, animation.getFrameList().get(0).Alarm);
            Assert.assertEquals(initialAlarm - elapsedTicks, animation.next_frame_delay);
        }
    }

    @Test
    public void testGetFrame_disabledAnim() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int[] frameValues = createFrameDef(0, 1, Animation.ANIM_DISABLED, 0);

        try (Animation animation = setupAnimationForFrameTest(1, frameValues, canvasRef, 0)) {
            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(1000L);
            animation.getFrame();

            verify(canvasRef.get(), times(0)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            // next_frame_delay is clamped to FRAME_RATE if no other alarms are pending
            Assert.assertEquals(Animation.FRAME_RATE, animation.next_frame_delay);
        }
    }

    @Test
    public void testGetFrame_colorXformAnim() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int initialAlarm = rand.nextInt(100);
        int elapsedTicks = initialAlarm << 1;

        int[] frameValues = createFrameDef(0, 1, Animation.COLORXFORM_ANIM | Animation.CIRCULAR_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(
                1,
                frameValues,
                canvasRef,
                initialAlarm
        )) {
            animation.getFrameList().get(0).CurIndex = 0;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn((long) elapsedTicks);
            animation.getFrame();

            verify(canvasRef.get(), times(0)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertEquals(0, animation.getFrameList().get(0).Alarm);
        }
    }

    @Test
    public void testGetFrame_randomAnim() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int numFrames = 10;
        int[] frameValues = createFrameDef(0, numFrames, Animation.RANDOM_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(numFrames, frameValues, canvasRef, 0)) {
            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(100L);

            animation.getFrame();

            int newCurIndex = animation.getFrameList().get(0).CurIndex;
            Assert.assertTrue(newCurIndex >= 0 && newCurIndex < numFrames);
            Assert.assertTrue(animation.getFrameList().get(0).Alarm > 0);
        }
    }

    @Test
    public void testGetFrame_circularAnim_increment() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int numFrames = 10;
        int startIndex = 0;
        int[] frameValues = createFrameDef(startIndex, numFrames, Animation.CIRCULAR_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(numFrames, frameValues, canvasRef, 0)) {
            // Set CurIndex such that it won't wrap immediately
            animation.getFrameList().get(0).CurIndex = startIndex;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(100L);
            animation.getFrame();

            verify(canvasRef.get(), times(1)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertEquals(startIndex + 1, animation.getFrameList().get(0).CurIndex);
        }
    }

    @Test
    public void testGetFrame_circularAnim_wrapAround() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int numFrames = 10;
        int startIndex = 0;
        int[] frameValues = createFrameDef(startIndex, numFrames, Animation.CIRCULAR_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(numFrames, frameValues, canvasRef, 0)) {
            // Set CurIndex to the last frame so it wraps
            animation.getFrameList().get(0).CurIndex = startIndex + numFrames - 1;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(100L);
            animation.getFrame();

            verify(canvasRef.get(), times(0)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertEquals(startIndex, animation.getFrameList().get(0).CurIndex);
            Assert.assertTrue(animation.getFrameList().get(0).Alarm > 0);
        }
    }

    @Test
    public void testGetFrame_yoyoAnim_upDir_increment() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int numFrames = 10;
        int startIndex = 0;
        int[] frameValues = createFrameDef(startIndex, numFrames, Animation.YOYO_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(numFrames, frameValues, canvasRef, 0)) {
            animation.getFrameList().get(0).CurIndex = startIndex;
            animation.getFrameList().get(0).Direction = Animation.Direction.UP_DIR;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(100L);
            animation.getFrame();

            verify(canvasRef.get(), times(1)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertEquals(startIndex + 1, animation.getFrameList().get(0).CurIndex);
        }
    }

    @Test
    public void testGetFrame_yoyoAnim_upDir_to_downDir() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int numFrames = 10;
        int startIndex = 0;
        int[] frameValues = createFrameDef(startIndex, numFrames, Animation.YOYO_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(numFrames, frameValues, canvasRef, 0)) {
            animation.getFrameList().get(0).CurIndex = startIndex + numFrames - 1;
            animation.getFrameList().get(0).Direction = Animation.Direction.UP_DIR;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(100L);
            animation.getFrame();

            verify(canvasRef.get(), times(1)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertEquals(startIndex + numFrames - 1, animation.getFrameList().get(0).CurIndex);
            Assert.assertEquals(Animation.Direction.DOWN_DIR, animation.getFrameList().get(0).Direction);
        }
    }

    @Test
    public void testGetFrame_yoyoAnim_downDir_decrement() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int numFrames = 10;
        int startIndex = 0;
        int[] frameValues = createFrameDef(startIndex, numFrames, Animation.YOYO_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(numFrames, frameValues, canvasRef, 0)) {
            animation.getFrameList().get(0).CurIndex = startIndex + numFrames - 1;
            animation.getFrameList().get(0).Direction = Animation.Direction.DOWN_DIR;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(100L);
            animation.getFrame();

            verify(canvasRef.get(), times(1)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertEquals(startIndex + numFrames - 2, animation.getFrameList().get(0).CurIndex);
        }
    }

    @Test
    public void testGetFrame_yoyoAnim_downDir_to_upDir() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int numFrames = 10;
        int startIndex = 0;
        int[] frameValues = createFrameDef(startIndex, numFrames, Animation.YOYO_ANIM, 0);

        try (Animation animation = setupAnimationForFrameTest(numFrames, frameValues, canvasRef, 0)) {
            animation.getFrameList().get(0).CurIndex = startIndex;
            animation.getFrameList().get(0).Direction = Animation.Direction.DOWN_DIR;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(100L);
            animation.getFrame();

            verify(canvasRef.get(), times(0)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertEquals(startIndex, animation.getFrameList().get(0).CurIndex);
            Assert.assertEquals(Animation.Direction.UP_DIR, animation.getFrameList().get(0).Direction);
        }
    }

    @Test
    public void testGetFrame_blockMask() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        // Use 2 frames so they don't wrap immediately and draw nothing.
        int[] def0 = createFrameDef(0, 2, Animation.CIRCULAR_ANIM, 0);
        int[] def1 = createFrameDef(0, 2, Animation.CIRCULAR_ANIM, 1); // Blocks on bit 0

        T.setFrameCount(2).setup(this);
        Content content = T.build(this);
        Canvas mockCanvas = mock(Canvas.class);
        canvasRef.set(mockCanvas);

        try (Animation animation = new Animation(content, List.of(def0, def1), mockCanvas)) {
            // Initialize CurIndex such that it doesn't wrap
            animation.getFrameList().get(0).CurIndex = 0;
            animation.getFrameList().get(1).CurIndex = 0;
            animation.getFrameList().get(0).Alarm = 0;
            animation.getFrameList().get(1).Alarm = 0;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn(100L);
            animation.getFrame();

            // Frame 0 should draw, Frame 1 should be blocked and have its alarm reset to restart rate
            verify(mockCanvas, times(1)).drawBitmap(any(Bitmap.class), any(Float.class), any(Float.class), any());
            Assert.assertTrue("Frame 1 alarm should be reset due to blocking", animation.getFrameList().get(1).Alarm > 0);
        }
    }

    @Test
    public void testGetFrame_frameRateAdjustment() throws Exception {
        AtomicReference<Canvas> canvasRef = new AtomicReference<>();
        int initialAlarm = 1;
        int elapsedTicks = 2;
        int frameRate = Animation.FRAME_RATE - 1; // 24

        int[] frameValues = createFrameDef(0, 2, Animation.CIRCULAR_ANIM, 0);
        frameValues[3] = frameRate;
        frameValues[4] = 1; // random part is 0

        try (Animation animation = setupAnimationForFrameTest(
                2,
                frameValues,
                canvasRef,
                initialAlarm
        )) {
            // Ensure no wrap-around
            animation.getFrameList().get(0).CurIndex = 0;

            mockedStaticSystemClock.when(SystemClock::uptimeMillis).thenReturn((long) elapsedTicks);
            animation.getFrame();

            // next_frame_delay should be clamped to FRAME_RATE (25) because 24 < 25
            Assert.assertEquals(Animation.FRAME_RATE, animation.next_frame_delay);
        }
    }

    @Test
    public void testGetFrame_NoFrames_UsesDefaultDelay() throws IOException {
        T.setFrameCount(1).setup(this);
        Content content = T.build(this);

        try (Animation animation = new Animation(content, List.of(), null)) {
            animation.getFrame();
            Assert.assertEquals(Animation.FRAME_RATE, animation.next_frame_delay);
        }
    }

    /** A helper to create the giant pile of mocks necessary to fake Android's resource loading.
        This should not be used by any test not explicitly testing the Animation() constructor;
        all other tests should use the @VisibleForTesting decorated constructor */
    private void animationMocksHelper(String alienRace, int[] frameValues) {
        String packageName = "package_" + createString();
        int alienRaceResId = rand.nextInt(1, 0xFFFF);
        int contentResId = rand.nextInt(1, 0xFFFF);
        int frameResId = rand.nextInt(1, 0xFFFF);
        String contentArrayName = alienRace + "_resid";
        String frameArrayName = alienRace + "_frames";

        when(mockContext.getPackageName()).thenReturn(packageName);
        when(mockResources.getIdentifier(eq(alienRace), eq("array"), eq(packageName)))
                .thenReturn(alienRaceResId);
        when(mockResources.getStringArray(eq(alienRaceResId)))
                .thenReturn(new String[]{contentArrayName, frameArrayName});
        when(mockResources.getIdentifier(eq(contentArrayName), eq("array"), eq(packageName)))
                .thenReturn(contentResId);
        when(mockResources.getStringArray(eq(contentResId)))
                .thenReturn(new String[]{alienRace});
        when(mockResources.getIdentifier(eq(frameArrayName), eq("array"), eq(packageName)))
                .thenReturn(frameResId);
        when(mockResources.getIntArray(eq(frameResId)))
                .thenReturn(frameValues);
    }

    private Animation setupAnimationForFrameTest(
            int contentFrameCount,
            int[] frameDefinition,
            AtomicReference<Canvas> canvasRef,
            int initialAlarm
    ) throws IOException {
        T.setFrameCount(contentFrameCount).setup(this);
        Content content = T.build(this);

        Canvas mockCanvas = mock(Canvas.class);
        canvasRef.set(mockCanvas);

        Animation animation = new Animation(content, List.of(frameDefinition), mockCanvas);
        animation.getFrameList().get(0).Alarm = initialAlarm;
        return animation;
    }
}
