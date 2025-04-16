/*
 * Copyright (C) 2011 Nicolas Simonds
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package net.submedia.android.uqmlivewallpaper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

//------------------------------------------------------------------------
// Animation - a port of the C structs used in the UQM code for
// this stuff
class Animation implements AutoCloseable {

    public static final String TAG = "UQMWallpaper.Animation";

    // comm frame rate according to UQM sources
    public static final int FRAME_RATE = (1000 / 40);
    static final int DEFAULT_FRAME_DELAY = 0x7FFFFFFF;
    public volatile int next_frame_delay;

    // The next index is randomly chosen.
    public static final byte RANDOM_ANIM = (1 << 0);
    // After the last index has been reached, the animation starts over.
    public static final byte CIRCULAR_ANIM = (1 << 1);
    // After the last index has been reached, the order that the
    // animation frames are used is reversed.
    public static final byte YOYO_ANIM = (1 << 2);
    // Mask of all animation types.
    public static final byte ANIM_MASK = (RANDOM_ANIM | CIRCULAR_ANIM | YOYO_ANIM);

    // This is set in AlienTalkDesc when the ambient animations should
    // stop at the end of the current animation cycle.
    // In AlienAmbientArray, this is set for those ambient animations
    // which can not be active while the talking animation is active.
    public static final byte WAIT_TALKING = (1 << 3);
    public static final byte PAUSE_TALKING = (1 << 4);
    public static final byte TALK_INTRO = (1 << 5);
    public static final byte TALK_DONE = (1 << 6);

    // Silly Java, this won't lose precision!  Stop complaining...
    public static final byte ANIM_DISABLED = (byte) (1 << 7);
    public static final byte COLORXFORM_ANIM = PAUSE_TALKING;

    public enum Direction {
        UP_DIR,
        DOWN_DIR,
        NO_DIR
    }

    private final List<Frame> frame;
    private Content content;
    private final Canvas canvas;
    private final Bitmap result;
    private final Random rand = new Random();

    // TFB_Random() equivalent: rand.nextInt(0xFF), rand.nextInt(0xFFFF,), etc.
    // GetTimeCounter() equivalent: SystemClock.uptimeMillis()
    private long LastTime = SystemClock.uptimeMillis();

    @VisibleForTesting
    interface ContentFactory {
        Content create(String[] alien_races, Context c, Supplier<Boolean> isCancelled) throws IOException;
    }

    private static ContentFactory sContentFactory = Content::new;

    @VisibleForTesting
    static void setContentFactory(ContentFactory factory) {
        sContentFactory = factory;
    }

    @SuppressLint("DiscouragedApi")
    Animation(String alien_race, Context c, Supplier<Boolean> isCancelled) throws Exception {

        // works around a crash bug with
        // android.content.res.getIdentifier() on 4.x
        if (alien_race == null)
            throw new Exception("no alien_race passed");

        final String PACKAGE_NAME = c.getPackageName();
        final Resources r = c.getResources();
        final int resid = r.getIdentifier(alien_race, "array", PACKAGE_NAME);

        if (resid == 0)
            throw new Exception("Could not find resource id for " + alien_race);

        this.frame = new ArrayList<>();

        boolean first = true;
        for (String res : r.getStringArray(resid)) {
            if (first) {
                this.content = sContentFactory.create(r.getStringArray(r.getIdentifier(res, "array", PACKAGE_NAME)), c, isCancelled);
                first = false;
            } else
                this.frame.add(new Frame(r.getIntArray(r.getIdentifier(res, "array", PACKAGE_NAME))));
        }
        final Bitmap bg = this.content.frame.get(0).content;
        this.result = bg.copy(Objects.requireNonNull(bg.getConfig()), true);
        this.canvas = new Canvas(this.result);
        Log.d(TAG, "Animation initialized for race: " + alien_race);
        Log.v(TAG, "Detailed animation data: " + this);
    }

    @VisibleForTesting
    Animation(Content content, List<int[]> frameDefinitions, @Nullable Canvas canvas) {
        this.content = content;
        this.frame = new ArrayList<>();
        for (int[] def : frameDefinitions) {
            this.frame.add(new Frame(def));
        }
        final Bitmap bg = this.content.frame.get(0).content;
        this.result = bg.copy(Objects.requireNonNull(bg.getConfig()), true);
        this.canvas = canvas != null ? canvas : new Canvas(this.result);
    }

    @Override
    public void close() throws IOException {
        if (content != null) content.close();
        if (result != null && !result.isRecycled()) result.recycle();
    }

    // Getters for testing purposes
    public List<Frame> getFrameList() {
        return frame;
    }

    public Content getContent() {
        return content;
    }

    private void DrawStamp(Content.Frame f) {
        this.canvas.drawBitmap(f.content, f.hotspot.x(), f.hotspot.y(), null);
    }

    // a simplified implementation of ambient_anim_task from the UQM sources
    //
    public synchronized Bitmap getFrame() {
        long CurTime = SystemClock.uptimeMillis();
        long ElapsedTicks = Long.min(CurTime - this.LastTime, Integer.MAX_VALUE);

        this.next_frame_delay = DEFAULT_FRAME_DELAY;
        this.LastTime = CurTime;
        int activeMask = 0;

        // scribble all the updates onto the canvas
        for (int i = 0; i < this.frame.size(); i++) {
            Frame f = this.frame.get(i);
            int ActiveBit = 1 << i;
            boolean drawFrame = true;

            // ...unless it's disabled
            if (ANIM_DISABLED == (f.AnimFlags & ANIM_DISABLED))
                continue;

            // ...or it's not time yet
            if (f.Alarm > ElapsedTicks) {
                f.Alarm -= (int) ElapsedTicks;
                drawFrame = false;
            }
            // If any animation that blocks this one is currently active, apply
            // the restart delay and skip
            else if ((activeMask & f.BlockMask) != 0) {
                f.Alarm = f.randomRestartRate();
                drawFrame = false;
            }
            if (!drawFrame) {
                if (f.Alarm < this.next_frame_delay)
                    this.next_frame_delay = f.Alarm;
                continue;
            }
            activeMask |= ActiveBit;
            // Log.v(TAG, "ActiveBit: %#x ActiveMask: %#x StartIndex: %d NumFrames: %d CurIndex: %d".formatted(ActiveBit, ActiveMask, f.StartIndex, f.NumFrames, f.CurIndex));

            final int num_frames = f.NumFrames - 1;

            if (COLORXFORM_ANIM == (f.AnimFlags & COLORXFORM_ANIM)) {
                activeMask &= ~ActiveBit;
                f.Alarm = 0;
                drawFrame = false;
            } else if (YOYO_ANIM == (f.AnimFlags & YOYO_ANIM)) {
                if (f.Direction == Direction.UP_DIR) {
                    f.CurIndex++;
                    if (f.CurIndex > (f.StartIndex + num_frames)) {
                        f.Direction = Direction.DOWN_DIR;
                        f.CurIndex = (short) (f.StartIndex + num_frames);
                    }
                } else if (f.Direction == Direction.DOWN_DIR) {
                    f.CurIndex--;
                    if (f.CurIndex < f.StartIndex) {
                        f.Direction = Direction.UP_DIR;
                        f.CurIndex = f.StartIndex;
                        f.Alarm = f.randomRestartRate();
                        activeMask &= ~ActiveBit;
                        drawFrame = false;
                    }
                }
            } else if (CIRCULAR_ANIM == (f.AnimFlags & CIRCULAR_ANIM)) {
                f.CurIndex++;
                if (f.CurIndex > (f.StartIndex + num_frames)) {
                    f.CurIndex = f.StartIndex;
                    f.Alarm = f.randomRestartRate();
                    activeMask &= ~ActiveBit;
                    drawFrame = false;
                }
            } else if (RANDOM_ANIM == (f.AnimFlags & RANDOM_ANIM)) {
                f.CurIndex = (short) (f.StartIndex + rand.nextInt(f.NumFrames));
                activeMask &= ~ActiveBit;
            }

            // Advance happened above; now draw the updated frame position.
            // Skip the draw when we've just applied a restart delay (cycle boundary).
            // setup next iteration alarm (only if not already set at boundary above)
            if (drawFrame) {
                DrawStamp(this.content.frame.get(f.CurIndex));
                f.Alarm = f.randomFrameRate();
            }
            if (f.Alarm < this.next_frame_delay)
                this.next_frame_delay = f.Alarm;
            // Log.d(TAG, f.toString());
        }

        if (this.next_frame_delay < FRAME_RATE || this.next_frame_delay == DEFAULT_FRAME_DELAY)
            this.next_frame_delay = FRAME_RATE;

        return this.result;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.lineSeparator();

        for (Frame f : this.frame)
            result.append(f.toString()).append(NEW_LINE);
        result.append(this.content.toString());

        return result.toString();
    }

    //------------------------------------------------------------------------
    // Animation.Frame - The animation data with associated content
    //
    // These fields are 2x the size of their C counterparts, to avoid nonsense with sign bits.
    class Frame {
        // Index of the first image
        public final int StartIndex;
        // Number of frames in the animation.
        public final short NumFrames;
        // One of RANDOM_ANIM, CIRCULAR_ANIM, or YOYO_ANIM
        public final short AnimFlags;
        public final int BaseFrameRate;
        public final int RandomFrameRate;
        public final int BaseRestartRate;
        public final int RandomRestartRate;
        // Bit mask of the indices of all animations that can not
        // be active at the same time as this animation.
        public final int BlockMask;
        public Direction Direction;
        public int CurIndex;
        public int Alarm;

        Frame(int[] i) {
            this.StartIndex = 0xFFFF & i[0];
            this.NumFrames = (short) Math.max(1, i[1] & 0xFF);
            this.AnimFlags = (short) ((i[2] & Animation.ANIM_MASK) != 0
                    ? (i[2] & 0xFF)
                    : Animation.ANIM_DISABLED);
            this.BaseFrameRate = Math.max(1, 0xFFFF & i[3]);
            this.RandomFrameRate = Math.max(1, 0xFFFF & i[4]);
            this.BaseRestartRate = Math.max(1, 0xFFFF & i[5]);
            this.RandomRestartRate = Math.max(1, 0xFFFF & i[6]);
            this.BlockMask = i[7];

            this.Direction = Animation.Direction.UP_DIR;
            // For YOYO, initialize to the first frame; for all others, initialize to the last
            // frame, so that the first advance wraps and lands on StartIndex.
            this.CurIndex = (AnimFlags & YOYO_ANIM) != 0
                    ? this.StartIndex
                    : this.StartIndex + (this.NumFrames - 1);
            this.Alarm = this.randomRestartRate();
        }

        public int randomFrameRate() {
            return BaseFrameRate + rand.nextInt(RandomFrameRate);
        }

        public int randomRestartRate() {
            return BaseRestartRate + rand.nextInt(RandomRestartRate);
        }

        @NonNull
        @Override
        public String toString() {
            return "Start[%05d] Frames[%02d] Flags[%02d] FrameRate[%05d] FrameRate2[%05d] Restart[%05d] Restart2[%05d] Block[%010d]".formatted(
                    StartIndex, NumFrames, AnimFlags, BaseFrameRate, RandomFrameRate, BaseRestartRate, RandomRestartRate, BlockMask);
        }
    }
}
// END Animation
//------------------------------------------------------------------------
