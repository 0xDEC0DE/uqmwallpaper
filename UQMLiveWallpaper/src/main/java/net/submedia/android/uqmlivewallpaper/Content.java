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
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

//------------------------------------------------------------------------
// Content objects take a zipfile and an alien race, and extract
// all the animation frames and info from it.  These ought be able to be used
// by the Engine class to figure out everything they need to

public class Content {

    private static final String TAG = "UQMWallpaper.Content";
    public List<Frame> frame;
    public ZipFile zipfile;

    // attempts to find the .ani file for the given alien_race, and loads all
    // of the contents described in it
    //
    // XXX: alien_races is a String array of all known names for a race,
    // which unfortunately means tracking content changes upstream...
    Content(String[] alien_races, Context c) throws IOException {
        this.frame = new ArrayList<Frame>();
        try {
            this.zipfile = setupContent(assetMatching(".uqm", c.getAssets().list("")), c);
            String[] races = listFilesMatching(".*comm/.*\\.ani");

            for (String file : races) {
                for (String alien_race : alien_races) {
                    // Java regexes are weird, and seem to have an implict
                    // ^ and $ glued onto the ends of them...
                    if (file.matches(".*/" + alien_race + "/.*")) {
                        for (String frame : aniToFileList(file)) this.frame.add(new Frame(frame));
                        return;
                    }
                }
            }
            this.zipfile.close();

            throw new IOException("error loading content, tried" + Arrays.toString(alien_races));
        } catch (Exception e) {
            // turn everything into an IOException.  might not be the best idea.
            throw new IOException("error loading content: " + e);
        }
    }

    private static String assetMatching(String match, String[] items) {
        for (String item : items) if (item.endsWith(match)) return item;
        return null;
    }

    /*
       java.util.zip.ZipFile is stupid, and only takes filenames; the AssetManager returns
       InputStreams or FileDescriptors only; the former is painfully slow; the latter is unusable.
       Abuse the cache and copy the content pack into the cache dir and read it back as a ZipFile.
    */
    private ZipFile setupContent(String zipfile, Context c) throws IOException {
        File cached = new File(c.getExternalCacheDir(), zipfile);
        try {
            ZipFile retval = new ZipFile(cached);
            Log.d(TAG, String.format(Locale.US, "cached content pack found at %s, using", cached));
            return retval;
        } catch (IOException ioe) {
            Log.d(TAG, String.format(Locale.US, "no cached content pack found at %s, copying", cached));
            InputStream fd = c.getAssets().open(zipfile, AssetManager.ACCESS_BUFFER);
            Log.d(TAG, String.format(Locale.US, "opened asset %s", fd.toString()));
            try (FileOutputStream out = new FileOutputStream(cached)) {
                byte[] buf = new byte[8192];
                int length;
                while ((length = fd.read(buf)) > 0) out.write(buf, 0, length);
            }
        }
        return new ZipFile(cached);
    }

    private String[]
    listFilesMatching(String match) throws IOException {
        Enumeration entries = this.zipfile.entries();
        List<String> files = new ArrayList<String>();

        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            String name = entry.getName();
            if (name.matches(match)) {
                files.add(name);
            }
        }
        return files.toArray(new String[0]);
    }

    private String[]
    aniToFileList(String ani) throws IOException {
        String basedir = new File(ani).getParent();
        List<String> entries = new ArrayList<String>();

        for (String line : new String(readFromContentPack(ani)).split("\n")) {
            entries.add(basedir + "/" + line);
        }

        return entries.toArray(new String[0]);
    }

    // returns a generic byte array of whatever you ask for.
    // will likely need to mogrify the results into something
    // useful (e.g., a String object)
    //
    private byte[]
    readFromContentPack(String file) throws IOException {
        ZipEntry entry = this.zipfile.getEntry(file);
        byte[] buffer = new byte[(int) entry.getSize()];
        new DataInputStream(zipfile.getInputStream(entry)).readFully(buffer);
        return buffer;
    }

    //------------------------------------------------------------------------
    // The following are for testing/debugging

    @NonNull
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        result.append(this.getClass().getName() + " Object {" + NEW_LINE);
        for (Frame f : this.frame) {
            result.append(" Frame:" + NEW_LINE)
                    .append("  Filename: " + f.filename + NEW_LINE)
                    .append("  " + f.hotspot.toString() + NEW_LINE)
                    .append("  " + f + NEW_LINE);
        }
        result.append("}");

        return result.toString();
    }

    //------------------------------------------------------------------------
    // CLASS DEFINITIONS
    //------------------------------------------------------------------------

    //------------------------------------------------------------------------
    // Content.Frame - The Bitmap data with associated hotspot info
    class Frame {
        public String filename;
        public Hotspot hotspot;
        public Bitmap content;

        // if the ANI file format ever changes, this will break horribly
        Frame(String def)
                throws IOException {
            String[] field = def.trim().split("\\s+", 5);
            this.filename = field[0];
            this.hotspot = new Hotspot(field[3], field[4]);
            byte[] input = readFromContentPack(this.filename);
            this.content = BitmapFactory.decodeByteArray(input, 0, input.length);
            if (this.content == null) {
                throw new IOException("Could not decode file " + this.filename);
            }
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.US, "Content: %dx%d", this.content.getWidth(),
                    this.content.getHeight());
        }

        //------------------------------------------------------------------------
        // Content.Frame.Hotspot - The x,y coordinates of the image in the
        // larger canvas
        class Hotspot {
            public float x, y;

            Hotspot(float x, float y) {
                this.x = (x > 0) ? x : -x;
                this.y = (y > 0) ? y : -y;
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
                return String.format(Locale.US, "Hotspot: (%1.2f, %1.2f)", this.x, this.y);
            }
        }
        // END Content.Frame.Hotspot
        //------------------------------------------------------------------------

    }
    // END Content.Frame
    //------------------------------------------------------------------------
}
