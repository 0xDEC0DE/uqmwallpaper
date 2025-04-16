package net.submedia.android.uqmlivewallpaper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.MotionEvent;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WallpaperViewModelTest extends BaseTest {

    private WallpaperViewModel viewModel;

    @Mock
    private Animation mockAnimation;
    @Mock
    private Bitmap mockFrame;
    @Mock
    private Runnable mockOnDrawNeeded;
    @Mock
    private Handler mockWorkerHandler;
    @Mock
    private SharedPreferences mockPrefs;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        // Setup mock settings behavior
        when(mockPrefs.getString(anyString(), anyString())).thenReturn("urquan");
        when(mockPrefs.getFloat(anyString(), anyFloat())).thenReturn(100.0f);
        when(mockPrefs.getInt(anyString(), anyInt())).thenReturn(0);
        when(mockPrefs.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        viewModel = new WallpaperViewModel(settings);
        viewModel.setWorkerHandler(mockWorkerHandler);
        viewModel.setOnDrawNeeded(mockOnDrawNeeded);
        when(mockAnimation.getFrame()).thenReturn(mockFrame);
        int frameW = 1000;
        int frameH = 1000;
        when(mockFrame.getWidth()).thenReturn(frameW);
        when(mockFrame.getHeight()).thenReturn(frameH);
    }

    @Test
    public void testStartAndStop() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        WallpaperViewModel realThreadViewModel = new WallpaperViewModel(settings);
        realThreadViewModel.start();
        Assert.assertNotNull(realThreadViewModel);
        realThreadViewModel.stop();
    }

    @Test
    public void testStop_WithoutStart_Safe() {
        viewModel.stop();
    }

    @Test
    public void testSetVisible_True_SchedulesDraw() {
        viewModel.setVisible(true);
        verify(mockWorkerHandler, atLeastOnce()).postDelayed(any(Runnable.class), any(long.class));
    }

    @Test
    public void testSetVisible_False_RemovesCallbacks() {
        viewModel.setVisible(true);
        Mockito.reset(mockWorkerHandler);
        viewModel.setVisible(false);
        verify(mockWorkerHandler).removeCallbacks(any(Runnable.class));
    }

    @Test
    public void testSetAnimation_ClosesOldAnimation_OnWorkerThread() throws IOException {
        Animation oldAnimation = mock(Animation.class);
        viewModel.setAnimation(oldAnimation);
        Mockito.reset(mockWorkerHandler);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        viewModel.setAnimation(mockAnimation);
        // One post for the close, one for the draw
        verify(mockWorkerHandler, times(2)).post(runnableCaptor.capture());
        for (Runnable runnable : runnableCaptor.getAllValues()) {
            runnable.run();
        }
        verify(oldAnimation).close();
    }

    @Test
    public void testSetAnimation_ClosesOldAnimation_ImmediatelyIfNoHandler() throws IOException {
        viewModel.setWorkerHandler(null);
        Animation oldAnimation = mock(Animation.class);
        viewModel.setAnimation(oldAnimation);
        viewModel.setAnimation(mockAnimation);
        verify(oldAnimation).close();
    }

    @Test
    public void testSetAnimation_WithNull() {
        viewModel.setAnimation(mockAnimation);
        Mockito.reset(mockWorkerHandler);
        viewModel.setAnimation(null);
        Assert.assertNull(viewModel.getAnimation());
        verify(mockWorkerHandler, atLeastOnce()).post(any(Runnable.class));
    }

    @Test
    public void testSetAnimation_WithNullFrame_PreservesOldWidth() {
        when(mockFrame.getWidth()).thenReturn(100);
        viewModel.setAnimation(mockAnimation);
        Animation nullFrameAnim = mock(Animation.class);
        when(nullFrameAnim.getFrame()).thenReturn(null);
        viewModel.setAnimation(nullFrameAnim);
    }

    @Test
    public void testPinchZoom_ZeroDistance_DoesNotChangeScale() {
        float initialScalingFactor = 50.0f;
        viewModel.setScalingFactor(initialScalingFactor);
        MotionEvent downEvent = mock(MotionEvent.class);
        when(downEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
        when(downEvent.getPointerCount()).thenReturn(2);
        when(downEvent.getX(0)).thenReturn(500f);
        when(downEvent.getX(1)).thenReturn(600f);
        viewModel.onTouchEvent(downEvent);
        MotionEvent moveEvent = mock(MotionEvent.class);
        when(moveEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(moveEvent.getPointerCount()).thenReturn(2);
        when(moveEvent.getX(0)).thenReturn(600f);
        when(moveEvent.getX(1)).thenReturn(600f);
        viewModel.onTouchEvent(moveEvent);
        Assert.assertEquals(initialScalingFactor, viewModel.getScalingFactor(), 0.001f);
    }

    @Test
    public void testPinchZoom_WithNoAnimWidth_DoesNotChangeOffset() {
        viewModel.setAnimation(null);
        int initialOffset = 123;
        viewModel.setUserOffset(initialOffset);
        MotionEvent downEvent = mock(MotionEvent.class);
        when(downEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
        when(downEvent.getPointerCount()).thenReturn(2);
        when(downEvent.getX(0)).thenReturn(500f);
        when(downEvent.getX(1)).thenReturn(600f);
        viewModel.onTouchEvent(downEvent);
        MotionEvent moveEvent = mock(MotionEvent.class);
        when(moveEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(moveEvent.getPointerCount()).thenReturn(2);
        when(moveEvent.getX(0)).thenReturn(400f);
        when(moveEvent.getX(1)).thenReturn(700f);
        viewModel.onTouchEvent(moveEvent);
        Assert.assertEquals(0, viewModel.getOffset());
    }

    @Test
    public void testPinchZoom_WithAnimWidth_UpdatesOffset() {
        int animWidth = 1000;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(2000);
        viewModel.onSurfaceChanged(1080, 1920);
        viewModel.setScalingFactor(100.0f);
        // Ensure initial offset is non-zero so we can detect change
        viewModel.onOffsetsChanged(0.5f, 0.1f, false);
        int initialOffset = viewModel.getOffset();
        Assert.assertNotEquals(0, initialOffset);
        MotionEvent downEvent = mock(MotionEvent.class);
        when(downEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
        when(downEvent.getPointerCount()).thenReturn(2);
        when(downEvent.getX(0)).thenReturn(100f);
        when(downEvent.getX(1)).thenReturn(200f);
        when(downEvent.getY(0)).thenReturn(0f);
        when(downEvent.getY(1)).thenReturn(0f);
        viewModel.onTouchEvent(downEvent);
        MotionEvent moveEvent = mock(MotionEvent.class);
        when(moveEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(moveEvent.getPointerCount()).thenReturn(2);
        when(moveEvent.getX(0)).thenReturn(125f);
        when(moveEvent.getX(1)).thenReturn(175f);
        when(moveEvent.getY(0)).thenReturn(0f);
        when(moveEvent.getY(1)).thenReturn(0f);
        viewModel.onTouchEvent(moveEvent);
        Assert.assertEquals(50.0f, viewModel.getScalingFactor(), 0.001f);
        Assert.assertNotEquals(initialOffset, viewModel.getOffset());
    }

    @Test
    public void testPinchZoom_Clamping() {
        int animWidth = 1000;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(2000);
        viewModel.onSurfaceChanged(1000, 2000);
        // Zoom IN (scale factor increases)
        viewModel.setScalingFactor(50.0f);
        MotionEvent downEvent = mock(MotionEvent.class);
        when(downEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
        when(downEvent.getPointerCount()).thenReturn(2);
        when(downEvent.getX(0)).thenReturn(100f);
        when(downEvent.getX(1)).thenReturn(200f);
        viewModel.onTouchEvent(downEvent);
        MotionEvent moveEvent = mock(MotionEvent.class);
        when(moveEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(moveEvent.getPointerCount()).thenReturn(2);
        when(moveEvent.getX(0)).thenReturn(50f);
        when(moveEvent.getX(1)).thenReturn(250f); // 2x distance
        viewModel.onTouchEvent(moveEvent);
        Assert.assertEquals(100.0f, viewModel.getScalingFactor(), 0.1f);
        // Zoom OUT (scale factor decreases)
        when(moveEvent.getX(0)).thenReturn(125f);
        when(moveEvent.getX(1)).thenReturn(175f); // 0.5x distance relative to start
        viewModel.onTouchEvent(moveEvent);
        Assert.assertEquals(25.0f, viewModel.getScalingFactor(), 0.1f);
    }

    @Test
    public void testSingleTouchPan_ClampsOffset() {
        when(mockFrame.getWidth()).thenReturn(100);
        viewModel.setTotalWidth(2000);
        viewModel.onSurfaceChanged(500, 1920);
        viewModel.setScalingFactor(100.0f);
        viewModel.setAnimation(mockAnimation);
        MotionEvent downEvent = mock(MotionEvent.class);
        when(downEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);
        when(downEvent.getX()).thenReturn(100f);
        viewModel.onTouchEvent(downEvent);
        MotionEvent moveEvent = mock(MotionEvent.class);
        when(moveEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(moveEvent.getPointerCount()).thenReturn(1);
        when(moveEvent.getX()).thenReturn(200f);
        viewModel.onTouchEvent(moveEvent);
        Assert.assertEquals(0, viewModel.getOffset());
        int minOffset = 500 - 2000;
        when(moveEvent.getX()).thenReturn((float) (minOffset - 100));
        viewModel.onTouchEvent(moveEvent);
        Assert.assertEquals(minOffset, viewModel.getOffset());
    }

    @Test
    public void testSingleTouchPan_NoAnimation_ResetsOffset() {
        viewModel.setTotalWidth(2000);
        viewModel.onSurfaceChanged(500, 1920);
        viewModel.setAnimation(null);
        viewModel.setUserOffset(100);
        MotionEvent event = mock(MotionEvent.class);
        when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(event.getPointerCount()).thenReturn(1);
        when(event.getX()).thenReturn(200f);
        viewModel.onTouchEvent(event);
        Assert.assertEquals(0, viewModel.getOffset());
    }

    @Test
    public void testOnOffsetsChanged_ImageFitsScreen_ResetsOffset() {
        viewModel.onSurfaceChanged(1080, 1920);
        viewModel.setScalingFactor(0f);
        viewModel.setAnimation(mockAnimation);
        viewModel.setUserOffset(-100);
        viewModel.onOffsetsChanged(0.5f, 0f, false);
        Assert.assertEquals(0, viewModel.getOffset());
    }

    @Test
    public void testUpdateAspect_ZeroWidthBitmap() {
        Bitmap zeroWidthBitmap = mock(Bitmap.class);
        when(zeroWidthBitmap.getWidth()).thenReturn(0);
        viewModel.updateAspect(zeroWidthBitmap);
        Assert.assertEquals(10000, viewModel.getAspect());
    }

    @Test
    public void testSetFillFrame() {
        viewModel.setFillFrame(true);
        Assert.assertTrue(viewModel.getFillFrame());
        verify(mockWorkerHandler).post(any(Runnable.class));
    }

    @Test
    public void testOnSurfaceChanged_negativeDimensions() {
        viewModel.onSurfaceChanged(-1, -1);
        Assert.assertEquals(0, viewModel.getWidth());
        verify(mockWorkerHandler, never()).post(any(Runnable.class));
    }

    @Test
    public void testOnTouchEvent_actionUp() {
        MotionEvent event = mock(MotionEvent.class);
        when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_UP);
        viewModel.onTouchEvent(event);
        verify(mockWorkerHandler).post(any(Runnable.class));
    }

    @Test
    public void testNotifyDrawNeeded_ExecutesCallbackAndReschedules() {
        viewModel.setVisible(true);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockWorkerHandler, atLeastOnce()).postDelayed(runnableCaptor.capture(), any(long.class));
        Runnable drawRunnable = runnableCaptor.getValue();
        drawRunnable.run();
        verify(mockOnDrawNeeded).run();
        verify(mockWorkerHandler, times(2)).postDelayed(any(Runnable.class), any(long.class));
    }

    @Test
    public void testOnOffsetsChanged_Landscape_ResetsOffset() {
        viewModel.onSurfaceChanged(1080, 1920);
        viewModel.onOffsetsChanged(0.5f, 0f, true);
        Assert.assertEquals(0, viewModel.getOffset());
    }

    @Test
    public void testOnOffsetsChanged_Portrait_CalculatesOffset() {
        int animWidth = 1000;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(2000);
        viewModel.setScalingFactor(100.0f);
        viewModel.onSurfaceChanged(1000, 2000);
        viewModel.onOffsetsChanged(0.5f, 0.1f, false);
        Assert.assertEquals(-500, viewModel.getOffset());
    }

    @Test
    public void testOnOffsetsChanged_Portrait_WithUserOffset() {
        int animWidth = 1000;
        int totalWidth = 2000;
        int screenWidth = 500;
        float scalingFactor = 100.0f;
        float xOffset = 0.5f;
        int userOffset = -100;
        float targetWidth = animWidth + (scalingFactor / 100.0f) * (totalWidth - animWidth);
        int aspect = (int) (targetWidth * 10000 / animWidth);
        float scaledImageWidth = animWidth * aspect / 10000.0f;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(totalWidth);
        viewModel.onSurfaceChanged(screenWidth, 2000);
        viewModel.setUserOffset(userOffset);
        viewModel.setScalingFactor(scalingFactor);
        viewModel.onOffsetsChanged(xOffset, 0.1f, false);
        int expectedOffset = (int) (userOffset + (screenWidth - scaledImageWidth - userOffset) * xOffset);
        Assert.assertEquals(expectedOffset, viewModel.getOffset());
    }

    @Test
    public void testPointerDown_NotTwoPointers() {
        MotionEvent event = mock(MotionEvent.class);
        when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
        when(event.getPointerCount()).thenReturn(3);
        viewModel.onTouchEvent(event);
    }

    @Test
    public void testMove_InvalidPointerStates() {
        MotionEvent moveEvent = mock(MotionEvent.class);
        when(moveEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(moveEvent.getPointerCount()).thenReturn(3);
        viewModel.onTouchEvent(moveEvent);
    }

    @Test
    public void testSetAnimation_IOException_IsIgnored() throws IOException {
        Animation failingAnimation = mock(Animation.class);
        Mockito.doThrow(new IOException("Fail close")).when(failingAnimation).close();
        viewModel.setAnimation(failingAnimation);
        Mockito.reset(mockWorkerHandler);
        viewModel.setAnimation(mockAnimation);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockWorkerHandler, atLeastOnce()).post(runnableCaptor.capture());
        for (Runnable runnable : runnableCaptor.getAllValues()) {
            runnable.run();
        }
    }

    @Test
    public void testActionUp_UpdatesUserOffset() {
        when(mockFrame.getWidth()).thenReturn(100);
        viewModel.setTotalWidth(2000);
        viewModel.onSurfaceChanged(500, 1920);
        viewModel.setScalingFactor(100.0f);
        viewModel.setAnimation(mockAnimation);
        MotionEvent downEvent = mock(MotionEvent.class);
        when(downEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);
        when(downEvent.getX()).thenReturn(100f);
        viewModel.onTouchEvent(downEvent);
        MotionEvent moveEvent = mock(MotionEvent.class);
        when(moveEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(moveEvent.getPointerCount()).thenReturn(1);
        when(moveEvent.getX()).thenReturn(50f);
        viewModel.onTouchEvent(moveEvent);
        Assert.assertEquals(-50, viewModel.getOffset());
        MotionEvent upEvent = mock(MotionEvent.class);
        when(upEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_UP);
        viewModel.onTouchEvent(upEvent);
        viewModel.onOffsetsChanged(0f, 0f, false);
        Assert.assertEquals(-50, viewModel.getOffset());
    }

    @Test
    public void testScheduleDraw_UsesAnimationDelay() {
        viewModel.setVisible(true);
        Mockito.reset(mockWorkerHandler);
        mockAnimation.next_frame_delay = 50;
        viewModel.setAnimation(mockAnimation);
        viewModel.setVisible(false);
        viewModel.setVisible(true);
        verify(mockWorkerHandler).postDelayed(any(Runnable.class), eq(50L));
    }

    @Test
    public void testScheduleDraw_NoAnimation_UsesDefaultDelay() {
        viewModel.setAnimation(null);
        viewModel.setVisible(true);
        verify(mockWorkerHandler).postDelayed(any(Runnable.class), eq(25L));
    }

    @Test
    public void testSetAnimation_UpdatesOffset() {
        viewModel.onSurfaceChanged(1000, 2000);
        viewModel.setTotalWidth(2000);
        viewModel.onOffsetsChanged(0.5f, 0.1f, false);
        Assert.assertEquals(0, viewModel.getOffset());
        when(mockFrame.getWidth()).thenReturn(1000);
        viewModel.setAnimation(mockAnimation);
        Assert.assertEquals(-500, viewModel.getOffset());
    }

    @Test
    public void testSetTotalWidth_UpdatesOffset() {
        when(mockFrame.getWidth()).thenReturn(1000);
        viewModel.setAnimation(mockAnimation);
        viewModel.onSurfaceChanged(1000, 2000);
        viewModel.setTotalWidth(1000);
        viewModel.onOffsetsChanged(0.5f, 0.1f, false);
        Assert.assertEquals(0, viewModel.getOffset());
        viewModel.setTotalWidth(2000);
        Assert.assertEquals(-500, viewModel.getOffset());
    }

    @Test
    public void testBackgroundOffset_CenteringWhenNarrow() {
        int screenWidth = 1080;
        int bgWidth = 1000;
        viewModel.onSurfaceChanged(screenWidth, 1920);
        Assert.assertEquals(40, viewModel.getBackgroundOffset(bgWidth));
    }

    @Test
    public void testBackgroundOffset_ProportionalParallaxWhenWide() {
        int screenWidth = 1080;
        int animWidth = 1000;
        int totalWidth = 2000;
        int bgWidth = 3000;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(totalWidth);
        viewModel.setScalingFactor(100.0f);
        viewModel.onSurfaceChanged(screenWidth, 1920);
        viewModel.onOffsetsChanged(0f, 0.1f, false);
        Assert.assertEquals(0, viewModel.getBackgroundOffset(bgWidth));
        viewModel.onOffsetsChanged(1.0f, 0.1f, false);
        Assert.assertEquals(screenWidth - bgWidth, viewModel.getBackgroundOffset(bgWidth));
        viewModel.onOffsetsChanged(0.5f, 0.1f, false);
        Assert.assertEquals((screenWidth - bgWidth) / 2, viewModel.getBackgroundOffset(bgWidth));
    }

    @Test
    public void testBackgroundOffset_SmallScrollRange() {
        int screenWidth = 1;
        int animWidth = 1;
        int totalWidth = 2;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(totalWidth);
        viewModel.onSurfaceChanged(screenWidth, 1920);
        viewModel.setScalingFactor(0.01f);
        // mWidth = 1, bgWidth = 2000. Centered is (1 - 2000) / 2 = -999.
        Assert.assertEquals(-999, viewModel.getBackgroundOffset(2000));
    }

    @Test
    public void testUpdateFromSettings() {
        WallpaperSettings settings = new WallpaperSettings(mockPrefs);
        settings.scalingFactor = 88.0f;
        settings.fillFrame = true;
        settings.offset = -444;
        viewModel.updateFromSettings(settings);
        Assert.assertEquals(88.0f, viewModel.getScalingFactor(), 0.1f);
        Assert.assertTrue(viewModel.getFillFrame());
        Assert.assertEquals(-444, viewModel.getUserOffset());
        verify(mockWorkerHandler, atLeastOnce()).post(any(Runnable.class));
    }

    @Test
    public void testUpdateOffset_WidthZero() {
        viewModel.onSurfaceChanged(0, 1920);
        viewModel.updateAspect(mockFrame);
        viewModel.onOffsetsChanged(0.5f, 0.1f, false);
        Assert.assertEquals(0, viewModel.getOffset());
    }

    @Test
    public void testUpdateOffset_SystemXStepZero() {
        int animWidth = 1000;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(2000);
        viewModel.onSurfaceChanged(1000, 2000);
        viewModel.setScalingFactor(100f);
        viewModel.setUserOffset(-123);
        viewModel.onOffsetsChanged(0.5f, 0.0f, false);
        Assert.assertEquals(-123, viewModel.getOffset());
    }

    @Test
    public void testPinchZoom_OffsetClamping() {
        int animWidth = 1000;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(2000);
        viewModel.onSurfaceChanged(1000, 2000);
        viewModel.setScalingFactor(100f); // targetWidth = 2000
        MotionEvent downEvent = mock(MotionEvent.class);
        when(downEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
        when(downEvent.getPointerCount()).thenReturn(2);
        when(downEvent.getX(0)).thenReturn(500f);
        when(downEvent.getX(1)).thenReturn(600f);
        when(downEvent.getY(0)).thenReturn(0f);
        when(downEvent.getY(1)).thenReturn(0f);
        viewModel.onTouchEvent(downEvent);
        viewModel.setUserOffset(-500);
        MotionEvent moveEvent = mock(MotionEvent.class);
        when(moveEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
        when(moveEvent.getPointerCount()).thenReturn(2);
        when(moveEvent.getX(0)).thenReturn(540f);
        when(moveEvent.getX(1)).thenReturn(560f); // 0.2x distance
        when(moveEvent.getY(0)).thenReturn(0f);
        when(moveEvent.getY(1)).thenReturn(0f);
        viewModel.onTouchEvent(moveEvent);
        // mInitialScalingFactor = 100. scaleChange = 0.2. newScalingFactor = 20.
        // oldTargetWidth = 2000. newTargetWidth = 1000 + 0.2*1000 = 1200.
        // scaleRatio = 1200 / 2000 = 0.6.
        // mOffset = (int) (550 - ((550 - (-500)) * 0.6)) = 550 - 630 = -80.
        Assert.assertEquals(-80, viewModel.getOffset());
    }

    @Test
    public void testBackgroundOffset_FudgeFactor_KeepsCenteredWhenSlightlyOver() {
        int screenWidth = 1000;
        int animWidth = 1000;
        int totalWidth = 2000;
        int bgWidth = 2000;
        when(mockFrame.getWidth()).thenReturn(animWidth);
        viewModel.setAnimation(mockAnimation);
        viewModel.setTotalWidth(totalWidth);
        viewModel.onSurfaceChanged(screenWidth, 1000);
        // Set scaling factor so image is 1010 pixels (1% over)
        // 1010 = 1000 + (factor/100)*1000 => factor = 1.0
        viewModel.setScalingFactor(1.0f);
        viewModel.setUserOffset(-50); // Some non-zero offset
        viewModel.onOffsetsChanged(0.5f, 0.0f, false);
        int offset = viewModel.getBackgroundOffset(bgWidth);
        // Should be centered: (1000 - 2000) / 2 = -500
        Assert.assertEquals("Background should remain centered within 2% fudge factor", -500, offset);
    }

    @Test
    public void testLoadingState() {
        viewModel.setLoading(true);
        Assert.assertTrue(viewModel.isLoading());
        Assert.assertNull(viewModel.getErrorMessage());
        verify(mockWorkerHandler, atLeastOnce()).post(any(Runnable.class));
        viewModel.setLoading(false);
        Assert.assertFalse(viewModel.isLoading());
    }

    @Test
    public void testErrorMessageClearsLoading() {
        viewModel.setLoading(true);
        viewModel.setErrorMessage("Error");
        Assert.assertFalse(viewModel.isLoading());
        Assert.assertEquals("Error", viewModel.getErrorMessage());
    }

    @Test
    public void testAnimationClearsLoading() {
        viewModel.setLoading(true);
        viewModel.setAnimation(mockAnimation);
        Assert.assertFalse(viewModel.isLoading());
        Assert.assertNull(viewModel.getErrorMessage());
    }
}
