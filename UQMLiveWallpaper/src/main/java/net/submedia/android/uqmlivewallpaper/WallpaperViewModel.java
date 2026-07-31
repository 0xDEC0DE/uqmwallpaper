package net.submedia.android.uqmlivewallpaper;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;

public class WallpaperViewModel {
    public static final String TAG = "UQMWallpaper.ViewModel";
    /** NOTE(nic): The "fill frame" effect will "follow" the on-screen animation with a parallax
     * effect if the animation is scaled larger than a single screen; smaller than a single screen
     * will be centered on the animation midpoint.  This "fudge factor" causes it to remain aligned
     * on the midpoint until it's wide enough to reasonably follow.  The value was picked entirely
     * by trial-and-error.
     */
    public static final float FILL_FRAME_FOLLOW_FUDGE_FACTOR = 1.52f;
    private Animation mAnim;
    private Runnable mOnDrawNeeded;
    private HandlerThread mWorkerThread;
    private Handler mWorkerHandler;
    private volatile boolean mVisible;
    private final Object mLock = new Object();
    private volatile int mWidth;
    private volatile int mHeight;
    private volatile int mTotalWidth;
    private volatile float mScalingFactor;
    private volatile boolean mFillFrame;
    private volatile int mUserOffset;
    private volatile int mAspect;
    private volatile int mOffset;
    private volatile float mSystemXOffset;
    private volatile float mSystemXStep;
    private volatile boolean mIsLandscape;
    private volatile int mAnchor = 0;
    private volatile float mInitialPointerDistance = -1;
    private volatile float mInitialScalingFactor;
    private final PointF mMidPoint = new PointF();
    private volatile String mErrorMessage;
    private volatile boolean mIsLoading;
    private WallpaperSettings mSettings;

    private volatile int mAnimWidth;
    private volatile int mAnimHeight;
    private final Rect mDestRect = new Rect();

    private final Runnable mDrawRunnable = this::notifyDrawNeeded;

    public WallpaperViewModel(@NonNull WallpaperSettings settings) {
        updateFromSettings(settings);
    }

    public void updateFromSettings(@NonNull WallpaperSettings settings) {
        synchronized (mLock) {
            this.mSettings = settings;
            this.mScalingFactor = settings.scalingFactor;
            this.mFillFrame = settings.fillFrame;
            this.mUserOffset = settings.offset;
            updateAspect();
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
        }
    }

    @VisibleForTesting
    public WallpaperSettings getSettings() {
        synchronized (mLock) { return mSettings; }
    }

    public void setOnDrawNeeded(Runnable onDrawNeeded) {
        synchronized (mLock) {
            this.mOnDrawNeeded = onDrawNeeded;
        }
    }

    public void start() {
        synchronized (mLock) {
            if (mWorkerThread == null) {
                mWorkerThread = new HandlerThread("WallpaperWorker");
                mWorkerThread.start();
                mWorkerHandler = new Handler(mWorkerThread.getLooper());
            }
        }
    }

    public void stop() {
        synchronized (mLock) {
            if (mWorkerThread != null) {
                mWorkerThread.quitSafely();
                mWorkerThread = null;
                mWorkerHandler = null;
            }
        }
    }

    @VisibleForTesting
    void setWorkerHandler(Handler handler) {
        synchronized (mLock) {
            this.mWorkerHandler = handler;
        }
    }

    public void setVisible(boolean visible) {
        synchronized (mLock) {
            mVisible = visible;
            if (mVisible) {
                scheduleDraw();
            } else {
                if (mWorkerHandler != null) mWorkerHandler.removeCallbacks(mDrawRunnable);
            }
        }
    }

    public boolean isVisible() { return mVisible; }

    private void scheduleDraw() {
        if (mVisible && mWorkerHandler != null) {
            mWorkerHandler.removeCallbacks(mDrawRunnable);
            int delay = (mAnim != null) ? mAnim.next_frame_delay : (1000 / 40);
            mWorkerHandler.postDelayed(mDrawRunnable, delay);
        }
    }

    private void notifyDrawNeeded() {
        Runnable callback;
        synchronized (mLock) {
            callback = mOnDrawNeeded;
            scheduleDraw(); // Re-schedule next frame
        }
        if (callback != null) callback.run();
    }

    public void setAnimation(Animation animation) {
        synchronized (mLock) {
            final Animation oldAnim = this.mAnim;
            if (oldAnim == animation && animation != null) return;

            this.mAnim = animation;
            if (animation != null) {
                this.mErrorMessage = null;
                this.mIsLoading = false;
                Bitmap b = animation.getFrame();
                if (b != null) {
                    mAnimWidth = b.getWidth();
                    mAnimHeight = b.getHeight();
                }
            } else {
                mAnimWidth = 0;
                mAnimHeight = 0;
            }
            updateAspect();

            if (oldAnim != null && oldAnim != animation) {
                if (mWorkerHandler != null) {
                    mWorkerHandler.post(() -> {
                        try {
                            oldAnim.close();
                        } catch (IOException ignored) {}
                    });
                } else {
                    try {
                        oldAnim.close();
                    } catch (IOException ignored) {}
                }
            }

            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
        }
    }

    /* NOTE(nic): `return`ing from inside `synchronized` blocks causes weirdness in the coverage
     *  tooling where it will claim that closing-braces are not covered.  Putting everything on one
     *  line papers over the issue.  So this is funny-looking, but for a reason.  Don't change it.
     */
    public Animation getAnimation() {
        synchronized (mLock) { return mAnim; }
    }

    public void setLoading(boolean loading) {
        synchronized (mLock) {
            this.mIsLoading = loading;
            if (loading) this.mErrorMessage = null;
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
        }
    }

    public boolean isLoading() { return mIsLoading; }

    public void setErrorMessage(String message) {
        synchronized (mLock) {
            this.mErrorMessage = message;
            if (message != null) this.mIsLoading = false;
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
        }
    }

    public String getErrorMessage() {
        synchronized (mLock) { return mErrorMessage; }
    }

    public void setTotalWidth(int width) {
        synchronized (mLock) {
            mTotalWidth = width;
            updateAspect();
        }
    }

    @VisibleForTesting
    public int getTotalWidth() { return mTotalWidth; }

    public void setScalingFactor(float factor) {
        synchronized (mLock) {
            this.mScalingFactor = factor;
            updateAspect();
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
        }
    }

    public float getScalingFactor() { return mScalingFactor; }

    public void setFillFrame(boolean fillFrame) {
        synchronized (mLock) {
            this.mFillFrame = fillFrame;
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
        }
    }

    public boolean getFillFrame() { return mFillFrame; }

    public void setUserOffset(int userOffset) {
        synchronized (mLock) {
            this.mUserOffset = userOffset;
            updateOffset();
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
        }
    }

    public int getUserOffset() { return mUserOffset; }

    public int getOffset() { return mOffset; }

    public int getAspect() { return mAspect; }

    public Rect getDestRect() {
        synchronized (mLock) { return new Rect(mDestRect); }
    }

    public void onSurfaceChanged(int width, int height) {
        if (width < 0 || height < 0) return;
        synchronized (mLock) {
            mWidth = width;
            mHeight = height;
            updateAspect();
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
            if (Log.isLoggable(TAG, Log.DEBUG))
                Log.d(TAG, "Surface dimensions updated: %dx%d (computed offset: %d)".formatted(width, height, mOffset));
        }
    }

    public void onOffsetsChanged(float xOffset, float xStep, boolean isLandscape) {
        synchronized (mLock) {
            this.mSystemXOffset = xOffset;
            this.mSystemXStep = xStep;
            this.mIsLandscape = isLandscape;
            updateOffset();
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
        }
    }

    private void updateOffset() {
        if (mIsLandscape) {
            mOffset = 0;
        } else {
            float scaledImageWidth = getScaledImageWidth();
            if (scaledImageWidth <= mWidth || mWidth == 0) {
                mOffset = 0;
            } else if (mSystemXStep == 0.0f) {
                mOffset = mUserOffset;
                mOffset = Math.max((int) (mWidth - scaledImageWidth), Math.min(0, mOffset));
            } else {
                mOffset = (int) (mUserOffset + (mWidth - scaledImageWidth - mUserOffset) * mSystemXOffset);
            }
        }
        updateDestRect();
    }

    private void updateDestRect() {
        if (mAnimWidth == 0 || mAnimHeight == 0) return;

        float scaledWidth = mAnimWidth * mAspect / 10000.0f;
        int aspectHeight = (int) (mAnimHeight * mAspect / 10000.0f);

        int x = scaledWidth > mWidth ? mOffset : (int) ((mWidth - scaledWidth) / 2);
        int y = (mHeight - aspectHeight) / 2;
        mDestRect.set(x, y, x + (int) scaledWidth, y + aspectHeight);
    }

    public int getBackgroundOffset(int bgWidth) {
        int retval;
        synchronized (mLock) {
            float scaledImageWidth = getScaledImageWidth();
            if (scaledImageWidth > mWidth * FILL_FRAME_FOLLOW_FUDGE_FACTOR) {
                float scrollRange = mWidth - scaledImageWidth;
                if (Math.abs(scrollRange) < 0.001f) return (mWidth - bgWidth) / 2;
                float parallaxIndex = mOffset / scrollRange;
                parallaxIndex = Math.max(0f, Math.min(1f, parallaxIndex));
                retval = (int) (parallaxIndex * (mWidth - bgWidth));
            } else {
                retval = (mWidth - bgWidth) / 2;
            }
        }
        return retval;
    }

    public void onTouchEvent(MotionEvent event) {
        synchronized (mLock) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mAnchor = (int) event.getX() - mOffset;
                    mInitialPointerDistance = -1;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        mInitialPointerDistance = getPointerDistance(event);
                        mInitialScalingFactor = mScalingFactor;
                        getMidPoint(event, mMidPoint);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() >= 2 && mInitialPointerDistance > 0) {
                        handlePinchZoom(event);
                    } else if (event.getPointerCount() == 1 && mInitialPointerDistance <= 0) {
                        handleSingleTouchPan(event);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mUserOffset = mOffset;
                    mInitialPointerDistance = -1;
                    break;
            }
            if (mWorkerHandler != null) mWorkerHandler.post(mDrawRunnable);
            if (Log.isLoggable(TAG, Log.VERBOSE))
                Log.v(TAG, "Touch interaction: offset=%d, scaling=%.2f(%dpx)".formatted(mOffset, mScalingFactor, (int) getScaledImageWidth()));
        }
    }

    private void handlePinchZoom(MotionEvent event) {
        float newDistance = getPointerDistance(event);
        if (newDistance == 0) return;

        float scaleChange = newDistance / mInitialPointerDistance;
        float oldScalingFactor = mScalingFactor;
        mScalingFactor = Math.min(100.0f, Math.max(0.0f, mInitialScalingFactor * scaleChange));

        if (mAnimWidth != 0) {
            float originalImageWidth = mAnimWidth;
            float oldTargetWidth = originalImageWidth + (oldScalingFactor / 100.0f) * (mTotalWidth - originalImageWidth);
            float newTargetWidth = originalImageWidth + (mScalingFactor / 100.0f) * (mTotalWidth - originalImageWidth);
            float scaleRatio = newTargetWidth / oldTargetWidth;

            mOffset = (int) (mMidPoint.x - ((mMidPoint.x - mOffset) * scaleRatio));

            float currentScaledImageWidth = originalImageWidth * mAspect / 10000.0f;
            float minOffset = (currentScaledImageWidth > mWidth) ? mWidth - currentScaledImageWidth : 0;
            mOffset = Math.max((int) minOffset, Math.min(0, mOffset));

            mUserOffset = mOffset;
        }
        updateAspect();
    }

    private void handleSingleTouchPan(MotionEvent event) {
        float scaledImageWidth = getScaledImageWidth();
        if (scaledImageWidth > mWidth) {
            mOffset = (int) event.getX() - mAnchor;
            if (mOffset > 0) mOffset = 0;
            int minOffset = (int) (mWidth - scaledImageWidth);
            if (mOffset < minOffset) mOffset = minOffset;
        } else {
            mOffset = 0;
        }
        updateDestRect();
    }

    public void updateAspect(Bitmap b) {
        synchronized (mLock) {
            mAnimWidth = (b != null) ? b.getWidth() : 0;
            mAnimHeight = (b != null) ? b.getHeight() : 0;
            updateAspect();
        }
    }

    private void updateAspect() {
        if (mAnimWidth == 0) {
            mAspect = 10000;
        } else {
            float targetWidth = mAnimWidth + (mScalingFactor / 100.0f) * (mTotalWidth - mAnimWidth);
            mAspect = (int) (targetWidth * 10000 / mAnimWidth);
        }
        updateOffset();
    }

    private float getScaledImageWidth() {
        if (mAnimWidth != 0) return mAnimWidth * mAspect / 10000.0f;
        return 0;
    }

    private float getPointerDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void getMidPoint(MotionEvent event, PointF point) {
        point.x = (event.getX(0) + event.getX(1)) / 2;
        point.y = (event.getY(0) + event.getY(1)) / 2;
    }

    public int getWidth() { return mWidth; }

    public int getHeight() { return mHeight; }
}
