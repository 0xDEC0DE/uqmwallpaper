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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.OperationCanceledException;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

//------------------------------------------------------------------------
// Content objects take a zipfile and an alien race, and extract
// all the animation frames and info from it.  These ought be able to be used
// by the Engine class to figure out everything they need to

public class Content implements AutoCloseable {

    private static final String TAG = "UQMWallpaper.Content";
    public final List<Frame> frame;
    public ZipFile zipfile;
    private AssetFileDescriptor afd;

    // attempts to find the .ani file for the given alien_race, and loads all
    // of the contents described in it
    //
    // XXX: alien_races is a String array of all known names for a race,
    // which unfortunately means tracking content changes upstream...
    Content(String[] alien_races, Context c, Supplier<Boolean> isCancelled) throws IOException {
        this.frame = new ArrayList<>();
        try {
            if (isCancelled.get()) throw new OperationCanceledException();
            String[] assets = c.getAssets().list("");
            if (assets == null) throw new IOException("Assets list is null");
            this.zipfile = setupContent(assetMatching(".uqm", assets), c);
            loadFrames(alien_races, isCancelled);
        } catch (Exception e) {
            if (e instanceof OperationCanceledException) throw e;
            // Chain exception
            throw new IOException("error loading content: " + e.getMessage(), e);
        }
    }

    private void loadFrames(String[] alien_races, Supplier<Boolean> isCancelled) throws IOException {
        List<String> races = listFilesMatching(".*comm/.*\\.ani");

        for (String file : races) {
            for (String alien_race : alien_races) {
                if (file.contains("/" + alien_race + "/")) {
                    for (String frame : aniToFileList(file)) {
                        if (isCancelled.get()) throw new OperationCanceledException();
                        this.frame.add(new Frame(frame, isCancelled));
                    }
                    return;
                }
            }
        }
        throw new IOException("error loading content, tried " + Arrays.toString(alien_races));
    }

    @Override
    public void close() throws IOException {
        if (this.zipfile != null) {
            this.zipfile.close();
            this.zipfile = null;
        }
        if (this.afd != null) {
            this.afd.close();
            this.afd = null;
        }
        for (Frame f : frame)
            if (f.content != null && !f.content.isRecycled())
                f.content.recycle();
    }

    protected static String assetMatching(String match, String[] items) throws IOException {
        return Arrays.stream(items)
                .filter(item -> item.endsWith(match))
                .findFirst()
                .orElseThrow(() -> new IOException("no files matching (%s)".formatted(match)));
    }

    /*
        Return a handle to the content pack stored in the app assets.

        AssetManager returns app assets as "anonymous" InputStreams, i.e., the backing file is not visible.
        ZipFile.Builder() accepts InputStreams, but it assumes that the stream is backed by a
        file that can be seen/opened, and that assumption does not hold.  Using ZipArchiveInputStream
        would be an option, and it is functional, but it cannot do random file access, so it is painfully
        slow to use.

        So instead, we'll get the offset and length of the asset inside the APK, and create/use a File-like
        object to read the asset in-place.  This requires that the asset is stored in the APK uncompressed
        (via noCompress in build.gradle).
    */
    protected ZipFile setupContent(String zipfile, Context c) throws IOException {
        this.afd = c.getAssets().openFd(zipfile);
        try {
            // Use the raw FileDescriptor from the AFD to ensure our BoundedSeekableByteChannel
            // correctly handles absolute offsets within the APK.
            FileChannel apkChannel = new FileInputStream(afd.getFileDescriptor()).getChannel();
            var subChannel = new BoundedSeekableByteChannel(apkChannel, afd.getStartOffset(), afd.getLength());
            if (Log.isLoggable(TAG, Log.INFO))
                Log.i(TAG, "Loading embedded %s from APK (offset=%d, len=%d)".formatted(zipfile, afd.getStartOffset(), afd.getLength()));
            return ZipFile.builder().setSeekableByteChannel(subChannel).get();
        } catch (IOException e) {
            if (this.afd != null) {
                try { this.afd.close(); } catch (IOException ignored) {}
                this.afd = null;
            }
            throw e;
        }
    }

    protected List<String> listFilesMatching(String match) throws IOException {
        return this.zipfile.stream()
                .map(ZipArchiveEntry::getName)
                .filter(name -> name.matches(match))
                .collect(Collectors.toList());
    }

    protected List<String>
    aniToFileList(String ani) throws IOException {
        Path basedir = Path.of(ani).getParent();
        List<String> entries = new ArrayList<>();

        for (String line : new String(readFromContentPack(ani), StandardCharsets.UTF_8).split("\n"))
            entries.add(basedir.resolve(line).toString());

        return entries;
    }

    // returns a generic byte array of whatever you ask for.
    // will likely need to mogrify the results into something
    // useful (e.g., a String object)
    //
    protected byte[] readFromContentPack(String file) throws IOException {
        var entry = this.zipfile.getEntry(file);
        if (entry == null) throw new IOException("Entry not found: " + file);

        try (InputStream is = zipfile.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }

    //------------------------------------------------------------------------
    // The following are for testing/debugging

    @NonNull
    @Override
    public String toString() {
        String frames = frame.stream().map(f -> """
                         Frame:
                          Filename: %s
                          %s
                          %s
                        """.formatted(f.filename, f.hotspot, f))
                .collect(Collectors.joining());

        return "%s Object {%n%s}".formatted(getClass().getName(), frames);
    }

    //------------------------------------------------------------------------
    // CLASS DEFINITIONS
    //------------------------------------------------------------------------

    //------------------------------------------------------------------------
    // Content.BoundedSeekableByteChannel - A {@link SeekableByteChannel} that provides a thread-safe, bounded view of another {@link SeekableByteChannel}.
    static class BoundedSeekableByteChannel implements SeekableByteChannel {
        private final SeekableByteChannel channel;
        private final long startOffset;
        private final long size;
        private long position;

        public BoundedSeekableByteChannel(SeekableByteChannel channel, long startOffset, long size) {
            this.channel = Objects.requireNonNull(channel, "channel is null");
            this.startOffset = startOffset;
            this.size = size;
            this.position = 0;
        }

        @Override
        public synchronized int read(ByteBuffer dst) throws IOException {
            if (position >= size) return -1;

            int maxToRead = (int) Math.min(dst.remaining(), size - position);
            if (maxToRead <= 0) return -1;

            int oldLimit = dst.limit();
            dst.limit(dst.position() + maxToRead);

            channel.position(startOffset + position);
            int bytesRead = channel.read(dst);
            dst.limit(oldLimit);

            if (bytesRead > 0) position += bytesRead;
            return bytesRead;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new NonWritableChannelException();
        }

        @Override
        public synchronized long position() throws IOException {
            return position;
        }

        @Override
        public synchronized SeekableByteChannel position(long newPosition) throws IOException {
            if (newPosition < 0 || newPosition > size)
                throw new IllegalArgumentException("Position out of bounds");
            this.position = newPosition;
            return this;
        }

        @Override
        public synchronized long size() throws IOException {
            return size;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            throw new NonWritableChannelException();
        }

        @Override
        public synchronized boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public synchronized void close() throws IOException {
            channel.close();
        }
    }
    // END Content.BoundedSeekableByteChannel
    //------------------------------------------------------------------------

    //------------------------------------------------------------------------
    // Content.Frame - The Bitmap data with associated hotspot info
    class Frame {
        public final String filename;
        public final Hotspot hotspot;
        public final Bitmap content;
        public final int width;
        public final int height;

        // if the ANI file format ever changes, this will break horribly
        Frame(String def, Supplier<Boolean> isCancelled) throws IOException {
            if (isCancelled.get()) throw new OperationCanceledException();
            String[] field = def.trim().split("\\s+", 5);
            filename = field[0];
            hotspot = new Hotspot(field[3], field[4]);

            ZipArchiveEntry entry = zipfile.getEntry(filename);
            if (entry == null) throw new IOException("Could not find entry for " + filename);

            // 16-bit bitmaps should support roughly 65,000 colours more than we need
            try (InputStream is = zipfile.getInputStream(entry)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                this.content = BitmapFactory.decodeStream(is, null, options);
            }

            if (this.content == null) throw new IOException("Could not decode file " + filename);
            this.width = this.content.getWidth();
            this.height = this.content.getHeight();
        }

        @NonNull
        @Override
        public String toString() {
            return "Content: %dx%d".formatted(width, height);
        }

        //------------------------------------------------------------------------
        // Content.Frame.Hotspot - The x,y coordinates of the image in the
        // larger canvas
        record Hotspot(float x, float y) {
            Hotspot {
                x = Math.abs(x);
                y = Math.abs(y);
            }

            Hotspot(int x, int y) {
                this((float) x, (float) y);
            }

            Hotspot(String x, String y) {
                this(Integer.parseInt(x), Integer.parseInt(y));
            }

            Hotspot(int x, String y) {
                this(x, Integer.parseInt(y));
            }

            Hotspot(String x, int y) {
                this(Integer.parseInt(x), y);
            }

            @NonNull
            @Override
            public String toString() {
                return "Hotspot: (%1.2f, %1.2f)".formatted(x, y);
            }
        }
        // END Content.Frame.Hotspot
        //------------------------------------------------------------------------

    }
    // END Content.Frame
    //------------------------------------------------------------------------
}
