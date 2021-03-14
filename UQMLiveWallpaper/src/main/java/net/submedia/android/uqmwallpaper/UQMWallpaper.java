/*
 * Copyright (C) 2011 Nicolas Simonds
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.submedia.android.uqmwallpaper;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import java.util.Locale;


public class UQMWallpaper
        extends WallpaperService {

    public static final String TAG = "UQMWallpaper";
    public static final String OFFSET_PREF = "offset";

    private final Handler mHandler = new Handler();
    private Context mContext;
    private Width totalWidth;
    private int totalHeight;
    private int mUserOffset;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        // what odd method names for these...
        WallpaperManager wm = WallpaperManager.getInstance(mContext);
        totalWidth = new Width(wm.getDesiredMinimumWidth());
        totalHeight = wm.getDesiredMinimumHeight();
        Log.d(TAG, String.format("totalWidth: %04d totalHeight: %04d", totalWidth.full, totalHeight));

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public Engine onCreateEngine() {
        return new CommsEngine();
    }

    private class Width {
        public int full;
        public int half;

        Width(int width) {
            this.full = width;
            this.half = -(width >> 1);
        }
    }

    class CommsEngine
            extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private final SharedPreferences mPrefs;
        private final Rect mRect = new Rect();
        private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private int mAnchor = 0;
        private int mOffset = 0;
        private int aspectHeight;
        private int mWidth;
        private int mHeight;
        private Animation mAnim;
        private int mScaling = 2;
        private boolean mVisible;
        private final Runnable mDrawComms = new Runnable() {
            public void run() {
                drawFrame();
            }
        };

        CommsEngine() {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            mPrefs.registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(mPrefs, null);
            Log.v(TAG, "started");
        }

        private void setAspect(Bitmap b) {
            int aspect = (((mScaling > 1) ? totalWidth.full : mWidth) * 10000) / b.getWidth();
            aspectHeight = (b.getHeight() * aspect) / 10000;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Log.d(TAG, String.format(Locale.US, "%s => %s", key, prefs.toString()));
            try {
                // TODO(nic): get the getString() defaults from the settings.xml defaults
                if (key == null) return;
                switch (key) {
                    case "race":
                        mAnim = new Animation(prefs.getString("race", "urquan"), mContext);
                        Log.d(TAG, mAnim.toString());
                        break;
                    case "scaling":
                        mScaling = Integer.parseInt(prefs.getString("scaling", "2"));
                        break;
                    case OFFSET_PREF:
                        mUserOffset = prefs.getInt(OFFSET_PREF, 0);
                        break;
                    default:
                        Log.d(TAG, "Unknown key changed: " + key);
                        break;
                }
                setAspect(mAnim.getFrame());
            } catch (Exception e) {
                Log.w(TAG, e.toString());
                for (StackTraceElement t : e.getStackTrace()) {
                    Log.d(TAG, t.toString());
                }
                mAnim = null;
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mHandler.removeCallbacks(mDrawComms);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible) {
                drawFrame();
            } else {
                mHandler.removeCallbacks(mDrawComms);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            mOffset += mUserOffset;
            Log.d(TAG, String.format(Locale.US, "width(%04d) height(%04d) offset(%04d)", mWidth, mHeight, mOffset));

            if (mAnim == null) {
                try {
                    // TODO(nic): get the getString() defaults from the settings.xml defaults
                    mScaling = Integer.parseInt(mPrefs.getString("scaling", "2"));
                    mAnim = new Animation(mPrefs.getString("race", "urquan"), mContext);
                    setAspect(mAnim.getFrame());
                } catch (Exception e) {
                    Log.w(TAG, e.toString());
                    for (StackTraceElement t : e.getStackTrace()) {
                        Log.d(TAG, t.toString());
                    }
                    mAnim = null;
                }
            } else {
                setAspect(mAnim.getFrame());
            }

            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            mVisible = false;
            mHandler.removeCallbacks(mDrawComms);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                                     float xStep, float yStep, int xPixels, int yPixels) {
            Display d = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
            if ((d.getRotation() & 1) == 1)
                mOffset = 0;
            else
                mOffset = (int) ((totalWidth.half - mUserOffset) * xOffset + mUserOffset);
            Log.d(TAG, String.format(Locale.US,
                    "xOffset(%f) yOffset(%f) xStep(%f) yStep(%f) xPixels(%d) yPixels(%d) mUserOffset(%d) mOffset(%d)",
                    xOffset, yOffset, xStep, yStep, xPixels, yPixels, mUserOffset, mOffset));
            drawFrame();
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            if (!this.isPreview()) return;
            if (mScaling != 2) return;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mAnchor = (int) event.getX() - mOffset;
                    break;
                case MotionEvent.ACTION_MOVE:
                    mOffset = (int) event.getX() - mAnchor;
                    if (mOffset > 0) mOffset = 0;
                    if (mOffset < totalWidth.half) mOffset = totalWidth.half;
                    break;
                case MotionEvent.ACTION_UP:
                    // NOTE(nic): it would be better to run this only once in onDestroy() for the preview,
                    // but that appears to race with onCreate() for the non-preview Engine.  So we spam
                    // them continuously into mPrefs instead.  C'est la vie.
                    Editor e = mPrefs.edit();
                    e.putInt(OFFSET_PREF, mOffset);
                    Log.d(TAG, String.format(Locale.US, "Saving offset to sharedPreferences: %s", e.commit()));
                    mUserOffset = mOffset;
                default:
                    return;
            }
            Log.d(TAG, String.format("mAnchor (%04d) moffset(%04d)", mAnchor, mOffset));
        }

        /*
         * Draw one frame of the animation.  You can do any drawing you want in
         * here.
         */
        void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();
            // comm frame rate according to UQM sources
            int delay = (1000 / 40);

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    // reset canvas
                    c.drawColor(Color.BLACK);
                    // draw something
                    if (mAnim != null) {
                        int x, y, w, h;
                        Bitmap b = mAnim.getFrame();

                        /*
                         * Center the animation output on the screen, scaling the image as needed.
                         * In the case of scaling mode #2 position it according to the virtual
                         * screen offset.  This allows for the "parallax effect" that some
                         * launchers support
                         */
                        if (b != null) {
                            switch (mScaling) {
                                case 1:
                                    x = 0;
                                    y = (mHeight - aspectHeight) / 2;
                                    w = mWidth;
                                    h = y + aspectHeight;
                                    break;
                                case 2:
                                    x = mOffset;
                                    y = (mHeight - aspectHeight) / 2;
                                    w = x + totalWidth.full;
                                    h = y + aspectHeight;
                                    break;
                                default:
                                    x = (mWidth - b.getWidth()) / 2;
                                    y = (mHeight - b.getHeight()) / 2;
                                    w = x + b.getWidth();
                                    h = y + b.getHeight();
                            }
                            mRect.set(x, y, w, h);
                            c.drawBitmap(b, null, mRect, mPaint);
                            if (this.isPreview() && mScaling == 2) {
                                w = mWidth / 2;
                                h = mHeight / 5;
                                TextPaint p = new TextPaint();
                                p.setAntiAlias(true);
                                p.setColor(Color.WHITE);

                                p.setTextAlign(Paint.Align.CENTER);
                                p.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
                                p.setTextSize(16 * getResources().getDisplayMetrics().density);
                                String text = mContext.getString(R.string.set_center);
                                StaticLayout.Builder builder = StaticLayout.Builder.obtain(text, 0, text.length(), p, mWidth);
                                StaticLayout l = builder.build();
                                c.translate(w, h - (l.getHeight() >> 1));
                                l.draw(c);
                            }
                        }
                        delay = mAnim.next_frame_delay;
                    }
                }
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }

            // Reschedule the next redraw
            mHandler.removeCallbacks(mDrawComms);
            if (mVisible) {
                mHandler.postDelayed(mDrawComms, delay);
            }
        }
    }
}
