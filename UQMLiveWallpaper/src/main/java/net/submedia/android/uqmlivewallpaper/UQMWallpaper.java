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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.service.wallpaper.WallpaperService;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class UQMWallpaper extends WallpaperService {
    public static final String TAG = "UQMWallpaper";
    public static final String OFFSET_PREF = "offset";

    public static final String PREFS_HOME = "prefs_home";
    public static final String PREFS_LOCK = "prefs_lock";

    // Standard command sent by most Android Wallpaper Pickers when the user confirms their selection.
    private static final String COMMAND_REAPPLY = "android.wallpaper.reapply";

    private Context mContext;
    private int totalWidth;
    private final List<CommsEngine> mActiveEngines = new ArrayList<>();
    private AnimationFactory mAnimationFactory = Animation::new;

    private static WallpaperSettings sLiveHomeSettings = null;
    private static WallpaperSettings sLiveLockSettings = null;
    private static WallpaperSettings sStagedSettings = null;

    /**
     * Used for non-blocking UI thread tasks, such as retrying wallpaper manager queries
     * and surface update scheduling.
     */
    private static final Handler sLifecycleHandler = new Handler(Looper.getMainLooper());

    public static WallpaperSettings getStagedSettings() {
        return sStagedSettings;
    }

    public static WallpaperSettings getLiveSettings() {
        return sLiveHomeSettings;
    }

    public static WallpaperSettings getLiveSettings(int flags) {
        return (flags & WallpaperManager.FLAG_LOCK) != 0 ? sLiveLockSettings : sLiveHomeSettings;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        WallpaperManager wm = WallpaperManager.getInstance(mContext);
        totalWidth = wm.getDesiredMinimumWidth();
        int totalHeight = wm.getDesiredMinimumHeight();
        if (Log.isLoggable(TAG, Log.DEBUG))
            Log.d(TAG, "onCreate: totalWidth: %04d totalHeight: %04d".formatted(totalWidth, totalHeight));

        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        migrateLegacyScaling(defaultPrefs);
        migrateToNamespacedPrefs(defaultPrefs);

        sLiveHomeSettings = new WallpaperSettings(getSharedPreferences(PREFS_HOME, MODE_PRIVATE));
        sLiveHomeSettings.setTargetFlags(WallpaperManager.FLAG_SYSTEM);
        sLiveLockSettings = new WallpaperSettings(getSharedPreferences(PREFS_LOCK, MODE_PRIVATE));
        sLiveLockSettings.setTargetFlags(WallpaperManager.FLAG_LOCK);

        if (Log.isLoggable(TAG, Log.DEBUG))
            Log.d(TAG, "Initial live settings loaded. Home: %s, Lock: %s".formatted(sLiveHomeSettings, sLiveLockSettings));
    }

    private void migrateToNamespacedPrefs(SharedPreferences defaultPrefs) {
        SharedPreferences homePrefs = getSharedPreferences(PREFS_HOME, MODE_PRIVATE);
        SharedPreferences lockPrefs = getSharedPreferences(PREFS_LOCK, MODE_PRIVATE);

        // Migration trigger: home prefs are empty AND default prefs have content
        if (homePrefs.getAll().isEmpty() && !defaultPrefs.getAll().isEmpty()) {
            if (Log.isLoggable(TAG, Log.INFO))
                Log.i(TAG, "Migrating default preferences to home/lock namespace.");
            copyPrefs(defaultPrefs, homePrefs);
            copyPrefs(defaultPrefs, lockPrefs);

            // Retirement: Safely clear legacy global preferences after migration
            defaultPrefs.edit().clear().apply();
            if (Log.isLoggable(TAG, Log.INFO))
                Log.i(TAG, "Legacy global preferences retired.");
        }
    }

    private void copyPrefs(SharedPreferences source, SharedPreferences dest) {
        Editor editor = dest.edit();
        for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
        }
        editor.apply();
    }

    public static void migrateLegacyScaling(SharedPreferences prefs) {
        if (prefs.contains(SettingsFragment.SCALING) && !prefs.contains(SettingsFragment.SCALING_FACTOR)) {
            String legacyValue = prefs.getString(SettingsFragment.SCALING, "2");
            float newFactor = "0".equals(legacyValue) ? 0.0f : 100.0f;

            prefs.edit()
                    .putFloat(SettingsFragment.SCALING_FACTOR, newFactor)
                    .remove(SettingsFragment.SCALING)
                    .apply();
        }
    }

    @Override
    public Engine onCreateEngine() {
        if (Log.isLoggable(TAG, Log.INFO))
            Log.i(TAG, "onCreateEngine");
        CommsEngine engine = new CommsEngine();
        synchronized (mActiveEngines) {
            mActiveEngines.add(engine);
        }
        return engine;
    }

    @VisibleForTesting
    void setAnimationFactory(AnimationFactory factory) {
        this.mAnimationFactory = factory;
    }

    @VisibleForTesting
    void setTotalWidth(int width) {
        this.totalWidth = width;
        synchronized (mActiveEngines) {
            for (CommsEngine engine : mActiveEngines) engine.getViewModel().setTotalWidth(width);
        }
    }

    interface AnimationFactory {
        Animation create(String race, Context c, java.util.function.Supplier<Boolean> isCancelled) throws Exception;
    }

    class CommsEngine
            extends Engine
            implements WallpaperSettings.OnSettingsChangedListener {

        private final Rect bgRect = new Rect();
        private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RenderNode blurNode = new RenderNode("blurNode");
        private final WallpaperViewModel mViewModel;
        private final ExecutorService mLoaderExecutor = Executors.newSingleThreadExecutor();

        private boolean mIsPreview;
        private int mWallpaperFlags;
        private boolean mCreated = false;
        private volatile boolean mIsVisible = false;
        private WallpaperSettings mSettings;

        CommsEngine() {
            mSettings = sLiveHomeSettings;
            mViewModel = new WallpaperViewModel(mSettings);
            mViewModel.setTotalWidth(totalWidth);
            mViewModel.setOnDrawNeeded(this::drawFrame);
            mViewModel.start();

            RenderEffect blurEffect = RenderEffect.createBlurEffect(45.5f, 45.5f, Shader.TileMode.CLAMP);
            blurNode.setRenderEffect(blurEffect);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            mIsPreview = isPreview();
            mWallpaperFlags = getWallpaperFlagsSafe();
            mCreated = true;
            setTouchEventsEnabled(true);
            if (Log.isLoggable(TAG, Log.INFO))
                Log.i(TAG, "Engine@%08x: onCreate(preview=%b, flags=%d)".formatted(System.identityHashCode(this), mIsPreview, mWallpaperFlags));

            if (mIsPreview) {
                synchronized (mActiveEngines) {
                    boolean anotherPreviewActive = false;
                    for (CommsEngine e : mActiveEngines) {
                        if (e != this && e.mCreated && e.mIsPreview) {
                            anotherPreviewActive = true;
                            break;
                        }
                    }
                    if (!anotherPreviewActive && sStagedSettings != null && sStagedSettings.getState() != WallpaperSettings.State.COMMITTED) {
                        if (Log.isLoggable(TAG, Log.DEBUG))
                            Log.d(TAG, "Engine@%08x: New preview session detected. Discarding abandoned staged settings.".formatted(System.identityHashCode(this)));
                        sStagedSettings = null;
                    }
                }

                if (sStagedSettings == null) {
                    if (Log.isLoggable(TAG, Log.DEBUG))
                        Log.d(TAG, "Engine@%08x: Creating new staged settings from live.".formatted(System.identityHashCode(this)));
                    WallpaperSettings source = getLiveSettings(mWallpaperFlags);
                    sStagedSettings = source.clone();
                    sStagedSettings.setState(WallpaperSettings.State.STAGED);
                } else {
                    if (Log.isLoggable(TAG, Log.DEBUG))
                        Log.d(TAG, "Engine@%08x: Resuming existing staged settings (State: %s).".formatted(System.identityHashCode(this), sStagedSettings.getState()));
                }
                if (mWallpaperFlags != 0) {
                    sStagedSettings.setTargetFlags(mWallpaperFlags);
                }
                mSettings = sStagedSettings;
            } else {
                synchronized (mActiveEngines) {
                    if (sStagedSettings != null && sStagedSettings.getState() == WallpaperSettings.State.STAGED) {
                        sStagedSettings.setTargetFlags(mWallpaperFlags != 0 ? mWallpaperFlags : WallpaperManager.FLAG_SYSTEM);
                        sStagedSettings.setState(WallpaperSettings.State.COMMITTED);
                    }
                    checkAndAdoptSettings("onCreate");
                }
                mSettings = getLiveSettings(mWallpaperFlags);
            }
            if (Log.isLoggable(TAG, Log.DEBUG))
                Log.d(TAG, "Engine@%08x: Initialized with settings: %s".formatted(System.identityHashCode(this), mSettings));

            mViewModel.updateFromSettings(mSettings);
            mSettings.addListener(this);
        }

        // Return default value if internal binder is not initialized (e.g. in some unit tests)
        @VisibleForTesting
        int getWallpaperFlagsSafe() {
            try {
                return getWallpaperFlags();
            } catch (NullPointerException e) {
                return WallpaperManager.FLAG_SYSTEM;
            }
        }

        /**
         * Pull-based adoption for Live engines.
         * Must be called within a synchronized(mActiveEngines) block.
         */
        private void checkAndAdoptSettings(String trigger) {
            if (mIsPreview) return;

            if (sStagedSettings != null && sStagedSettings.getState() == WallpaperSettings.State.COMMITTED) {
                // Expansion: If the staged settings were committed with a subset of this engine's
                // targets, expand them. This ensures "Both" works even if commitment was triggered
                // by a more narrow engine (like a preview engine).
                if (sStagedSettings.getTargetFlags() != 0 && (sStagedSettings.getTargetFlags() & mWallpaperFlags) != mWallpaperFlags) {
                    if (Log.isLoggable(TAG, Log.DEBUG))
                        Log.d(TAG, "Expanding staged targets from %d to include %d".formatted(sStagedSettings.getTargetFlags(), mWallpaperFlags));
                    sStagedSettings.setTargetFlags(sStagedSettings.getTargetFlags() | mWallpaperFlags);
                }
                if (Log.isLoggable(TAG, Log.INFO))
                    Log.i(TAG, "Engine@%08x (LIVE): Adopting COMMITTED staged settings [trigger: %s]".formatted(System.identityHashCode(this), trigger));
                performAdoption();
            } else {
                if (Log.isLoggable(TAG, Log.VERBOSE))
                    Log.v(TAG, "Engine@%08x (LIVE): Skipping adoption [trigger: %s, reason: none ready]".formatted(System.identityHashCode(this), trigger));
            }
        }

        /**
         * Performs the actual handoff from staged to live settings and persists to disk.
         * Must be called within a synchronized(mActiveEngines) block.
         */
        private void performAdoption() {
            if (sStagedSettings == null) return;

            int targets = sStagedSettings.getTargetFlags();
            if (targets == 0) targets = WallpaperManager.FLAG_SYSTEM;

            // Use a temporary clone to save without affecting live settings yet
            WallpaperSettings temp = sStagedSettings.clone();
            temp.setState(WallpaperSettings.State.LIVE);

            boolean success = true;
            if ((targets & WallpaperManager.FLAG_SYSTEM) != 0) {
                Editor editor = getSharedPreferences(PREFS_HOME, MODE_PRIVATE).edit();
                if (!temp.save(editor)) success = false;
                else sLiveHomeSettings.copyFrom(sStagedSettings);
            }
            if ((targets & WallpaperManager.FLAG_LOCK) != 0) {
                Editor editor = getSharedPreferences(PREFS_LOCK, MODE_PRIVATE).edit();
                if (!temp.save(editor)) success = false;
                else sLiveLockSettings.copyFrom(sStagedSettings);
            }

            if (!success) {
                if (Log.isLoggable(TAG, Log.ERROR))
                    Log.e(TAG, "Failed to persist settings to disk during adoption.");
                return;
            }

            if (Log.isLoggable(TAG, Log.INFO))
                Log.i(TAG, "Live settings handoff complete for targets: " + targets);
            sStagedSettings = null;

            // Force all engines to refresh from their respective updated live settings
            synchronized (mActiveEngines) {
                for (CommsEngine e : mActiveEngines) {
                    if (e.mIsPreview) continue;
                    WallpaperSettings correctLive = getLiveSettings(e.mWallpaperFlags);
                    if (e.mSettings != correctLive) {
                        e.mSettings.removeListener(e);
                        e.mSettings = correctLive;
                        e.mSettings.addListener(e);
                    }
                    e.mViewModel.updateFromSettings(correctLive);
                }
            }
        }

        @VisibleForTesting
        WallpaperViewModel getViewModel() {
            return mViewModel;
        }

        @Override
        public void onSettingsChanged(String key) {
            if (Log.isLoggable(TAG, Log.DEBUG))
                Log.d(TAG, "Engine@%08x: onSettingsChanged(%s)".formatted(System.identityHashCode(this), key));
            try {
                if (key == null) return;

                switch (key) {
                    case SettingsFragment.ALIEN_RACE -> {
                        if (mIsVisible) loadAnimation(mSettings.race);
                        else mViewModel.setAnimation(null);
                    }
                    case SettingsFragment.SCALING_FACTOR -> mViewModel.setScalingFactor(mSettings.scalingFactor);
                    case SettingsFragment.FILL_FRAME -> mViewModel.setFillFrame(mSettings.fillFrame);
                    case OFFSET_PREF -> mViewModel.setUserOffset(mSettings.offset);
                    default -> Log.w(TAG, "Engine@%08x: Unknown key changed: %s".formatted(System.identityHashCode(this), key));
                }
            } catch (Exception e) {
                Log.w(TAG, "Engine@%08x: %s".formatted(System.identityHashCode(this), e));
                mViewModel.setAnimation(null);
            }
        }

        private void loadAnimation(String race) {
            if (mLoaderExecutor.isShutdown()) return;
            if (Log.isLoggable(TAG, Log.DEBUG))
                Log.d(TAG, "Engine@%08x: Loading animation for %s".formatted(System.identityHashCode(this), race));
            mViewModel.setLoading(true);
            try {
                mLoaderExecutor.execute(() -> {
                    try {
                        if (!mIsVisible) return;
                        Animation anim = mAnimationFactory.create(race, mContext, () -> !mIsVisible);
                        if (!mIsVisible) {
                            anim.close();
                            return;
                        }
                        mViewModel.setAnimation(anim);
                        mViewModel.updateAspect(anim.getFrame());
                        if (Log.isLoggable(TAG, Log.DEBUG))
                            Log.d(TAG, "Engine@%08x: Successfully loaded animation for %s".formatted(System.identityHashCode(this), race));
                    } catch (OperationCanceledException e) {
                        if (Log.isLoggable(TAG, Log.INFO))
                            Log.i(TAG, "Engine@%08x: Loading cancelled for %s".formatted(System.identityHashCode(this), race));
                    } catch (Exception e) {
                        if (Log.isLoggable(TAG, Log.WARN))
                            Log.w(TAG, "Engine@%08x: Failed to load animation: %s".formatted(System.identityHashCode(this), race), e);
                        mViewModel.setErrorMessage(mContext.getString(R.string.error_loading_alien, race));
                        mViewModel.setAnimation(null);
                    } finally {
                        mViewModel.setLoading(false);
                    }
                });
            } catch (RejectedExecutionException ignored) {
                mViewModel.setLoading(false);
            }
        }

        @Override
        public void onDestroy() {
            if (Log.isLoggable(TAG, Log.INFO))
                Log.i(TAG, "Engine@%08x: onDestroy(preview=%b, flags=%d)".formatted(System.identityHashCode(this), mIsPreview, mWallpaperFlags));
            synchronized (mActiveEngines) {
                mActiveEngines.remove(this);
            }
            if (mSettings != null) mSettings.removeListener(this);
            mLoaderExecutor.shutdownNow();
            mViewModel.stop();

            Animation anim = mViewModel.getAnimation();
            if (anim != null) try {
                anim.close();
            } catch (IOException ignored) {}
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (Log.isLoggable(TAG, Log.INFO))
                Log.i(TAG, "Engine@%08x: onVisibilityChanged(%b) [preview=%b, flags=%d]".formatted(System.identityHashCode(this), visible, mIsPreview, mWallpaperFlags));
            mIsVisible = visible;
            mViewModel.setVisible(visible);
            if (visible) {
                if (mViewModel.getAnimation() == null && !mViewModel.isLoading()) init_mAnim();
            } else {
                mViewModel.setAnimation(null);
            }
        }

        private void init_mAnim() {
            if (Log.isLoggable(TAG, Log.DEBUG))
                Log.d(TAG, "Engine@%08x: Triggering initial animation load.".formatted(System.identityHashCode(this)));
            mViewModel.setScalingFactor(mSettings.scalingFactor);
            mViewModel.setFillFrame(mSettings.fillFrame);
            mViewModel.setUserOffset(mSettings.offset);
            loadAnimation(mSettings.race);
        }

        @Override
        public void onDesiredSizeChanged(int desiredWidth, int desiredHeight) {
            super.onDesiredSizeChanged(desiredWidth, desiredHeight);
            if (Log.isLoggable(TAG, Log.DEBUG))
                Log.d(TAG, "Engine@%08x: onDesiredSizeChanged(w=%d, h=%d)".formatted(System.identityHashCode(this), desiredWidth, desiredHeight));
            applyNewTotalWidth(desiredWidth, 0);
        }

        private void updateTotalWidthFromManager(int currentSurfaceWidth) {
            WallpaperManager wm = WallpaperManager.getInstance(mContext);
            int newTotalWidth = wm.getDesiredMinimumWidth();
            applyNewTotalWidth(newTotalWidth, currentSurfaceWidth);
        }

        private void applyNewTotalWidth(int newTotalWidth, int currentSurfaceWidth) {
            if (newTotalWidth <= 0) return;

            if (newTotalWidth != totalWidth) {
                if (Log.isLoggable(TAG, Log.DEBUG))
                    Log.d(TAG, "Engine@%08x: Updating totalWidth from %d to %d".formatted(System.identityHashCode(this), totalWidth, newTotalWidth));
                setTotalWidth(newTotalWidth);
            }

            if (currentSurfaceWidth > 0 && newTotalWidth == currentSurfaceWidth) {
                if (Log.isLoggable(TAG, Log.DEBUG))
                    Log.d(TAG, "Engine@%08x: Total width (%d) matches surface width. Scheduling re-check.".formatted(System.identityHashCode(this), newTotalWidth));
                sLifecycleHandler.postDelayed(() -> updateTotalWidthFromManager(0), 500);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            if (Log.isLoggable(TAG, Log.DEBUG))
                Log.d(TAG, "Engine@%08x: onSurfaceChanged(w=%d, h=%d)".formatted(System.identityHashCode(this), width, height));
            if (width < 0 || height < 0) return;

            updateTotalWidthFromManager(width);

            mViewModel.onSurfaceChanged(width, height);
            Animation anim = mViewModel.getAnimation();
            if (anim != null) {
                if (Log.isLoggable(TAG, Log.DEBUG))
                    Log.d(TAG, "Engine@%08x: onSurfaceChanged: Animation exists, updating aspect.".formatted(System.identityHashCode(this)));
                mViewModel.updateAspect(anim.getFrame());
            }
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                                     float xStep, float yStep, int xPixels, int yPixels) {
            if (Log.isLoggable(TAG, Log.VERBOSE))
                Log.v(TAG, "Engine@%08x: onOffsetsChanged(xOff=%.2f)".formatted(System.identityHashCode(this), xOffset));
            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            mViewModel.onOffsetsChanged(xOffset, xStep, isLandscape);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            if (!mIsPreview) return;
            if (Log.isLoggable(TAG, Log.VERBOSE))
                Log.v(TAG, "onTouchEvent: " + event);
            mViewModel.onTouchEvent(event);
            mSettings.updateOffset(mViewModel.getUserOffset());
            mSettings.updateScalingFactor(mViewModel.getScalingFactor());
        }

        void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();
            if (!holder.getSurface().isValid()) return;

            final Canvas c = holder.lockHardwareCanvas();
            if (c == null) return;
            try {
                WallpaperViewModel vm = getViewModel();
                Animation anim = vm.getAnimation();
                int mWidth = vm.getWidth();
                int mHeight = vm.getHeight();
                boolean mFillFrame = vm.getFillFrame();
                Rect destRect = vm.getDestRect();

                c.drawColor(Color.BLACK);
                if (anim == null) {
                    String error = vm.getErrorMessage();
                    if (error != null) {
                        drawStatusMessage(c, error, mWidth, mHeight, Typeface.BOLD_ITALIC);
                    } else if (vm.isLoading()) {
                        drawStatusMessage(c, mContext.getString(R.string.loading_assets), mWidth, mHeight, Typeface.ITALIC);
                    }
                    return;
                }
                Bitmap b = anim.getFrame();
                if (b == null) return;

                if (mFillFrame) {
                    mPaint.setAlpha(0x7F);
                    int bgWidth = (int) (mHeight * ((float) b.getWidth() / b.getHeight()));
                    int bgX = vm.getBackgroundOffset(bgWidth);
                    bgRect.set(bgX, 0, bgX + bgWidth, mHeight);

                    blurNode.setPosition(0, 0, c.getWidth(), c.getHeight());
                    Canvas recordingCanvas = blurNode.beginRecording();
                    recordingCanvas.drawBitmap(b, null, bgRect, mPaint);
                    blurNode.endRecording();
                    c.drawRenderNode(blurNode);
                    mPaint.setAlpha(0xFF);
                }
                c.drawBitmap(b, null, destRect, mPaint);
                if (mIsPreview) {
                    String hint = (destRect.width() > mWidth)
                            ? mContext.getString(R.string.hint_drag_to_center)
                            : mContext.getString(R.string.hint_pinch_only);
                    drawStatusMessage(c, hint, mWidth, mHeight, Typeface.BOLD_ITALIC);
                }
            } finally {
                holder.unlockCanvasAndPost(c);
            }
        }

        private void drawStatusMessage(Canvas c, String text, int width, int height, int style) {
            float density = getResources().getDisplayMetrics().density;
            TextPaint p = new TextPaint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setShadowLayer(5.0f * density, 3.0f * density, 3.0f * density, Color.BLACK);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.defaultFromStyle(style));
            p.setTextSize(16 * density);

            StaticLayout l = StaticLayout.Builder.obtain(text, 0, text.length(), p, width).build();
            c.save();
            c.translate(width / 2f, (height / 2f) - (l.getHeight() >> 1));
            l.draw(c);
            c.restore();
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (Log.isLoggable(TAG, Log.VERBOSE))
                Log.v(TAG, "Engine@%08x: onCommand(action=%s, flags=%d)".formatted(System.identityHashCode(this), action, mWallpaperFlags));
            if (COMMAND_REAPPLY.equals(action) && sStagedSettings != null) {
                if (Log.isLoggable(TAG, Log.INFO))
                    Log.i(TAG, "Engine@%08x: REAPPLY received. Committing staged settings for adoption.".formatted(System.identityHashCode(this)));

                synchronized (mActiveEngines) {
                    // The user confirmed selection. Update target flags to match this engine's
                    // destination, even if they were previously narrowed by a preview engine.
                    sStagedSettings.setTargetFlags(mWallpaperFlags != 0 ? mWallpaperFlags : WallpaperManager.FLAG_SYSTEM);
                    sStagedSettings.setState(WallpaperSettings.State.COMMITTED);
                    checkAndAdoptSettings("onCommand:reapply");
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }
    }
}
