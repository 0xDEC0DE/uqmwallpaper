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

package net.submedia.android.uqmwallpaper;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

//------------------------------------------------------------------------
// Content objects take the name if a zipfile and an alien race, and extract
// all the animation frames and info from it.  These ought be able to be used
// by the Engine class to figure out everything they need to

public class Content {

	// class definitions for these types are at the end of the file
	public List<Frame> frame;

	// attempts to find the .ani file for the given alien_race, and loads all
	// of the contents described in it
	//
	// XXX: alien_races is a String array of all known names for a race,
	// which unfortunately means tracking content changes upstream...
	Content(String zipfile, String[] alien_races)
	throws IOException {
		this.frame = new ArrayList<Frame>();
		try {
			ZipFile z = new ZipFile(zipfile);
			String[] races = listFilesMatching(z, "comm/", ".ani");

			for (String file : races) {
				for (String alien_race : alien_races) {
					// Java regexes are weird, and seem to have an implict
					// ^ and $ glued onto the ends of them...
					if (file.matches(".*/" + alien_race + "/.*")) {
						for (String frame : aniToFileList(z, file)) {
							this.frame.add(new Frame(z, frame));
						}
						return;
					}
				}
			}
			z.close();

			throw new IOException("error loading content, tried" + Arrays.toString(alien_races));
		} catch (IOException ioe) {
			throw ioe;
		} catch (Exception e) {
			// turn everything into an IOException.  might not be the best idea.
			throw new IOException("error loading content: " + e.toString());
		}
	}

	// A stubbier constructor, just looks for .ani files and returns an empty
	// object.  Used to determine if a given file is a valid content pack
	Content(String zipfile)
	throws IOException {
		ZipFile z = new ZipFile(zipfile);
		String[] races = listFilesMatching(z, "comm/", ".ani");
		z.close();

		if (races.length < 1)
			throw new IOException("no content found");
	}

	private static String[]
	listFilesMatching(ZipFile zipfile, String prefix, String suffix) {
		Enumeration entries = zipfile.entries();
		List<String> files = new ArrayList<String>();

		while(entries.hasMoreElements()) {
			ZipEntry entry = (ZipEntry)entries.nextElement();
			String name = entry.getName();
			if (name.contains(prefix) && name.endsWith(suffix)) {
				files.add(name);
			}
		}
		return files.toArray(new String[0]);
	}

	private static String[]
	aniToFileList(ZipFile zipfile, String ani)
	throws IOException {
		String basedir = new File(ani).getParent();
		List<String> entries = new ArrayList<String>();

		try {
			for (String line : new String(readFromContentPack(zipfile, ani)).split("\n")) {
				entries.add(basedir + "/" + line);
			}
		}
		catch (IOException ioe) {
			throw ioe;
		}

		return entries.toArray(new String[0]);
	}

	// returns a generic byte array of whatever you ask for.
	// will likely need to mogrify the results into something
	// useful (e.g., a String object)
	//
	private static byte[]
	readFromContentPack(ZipFile zipfile, String file)
	throws IOException {
		ZipEntry entry = zipfile.getEntry(file);
		byte[] buffer = new byte[(int) entry.getSize()];
		new DataInputStream(zipfile.getInputStream(entry)).readFully(buffer);

		return buffer;
	}

	//------------------------------------------------------------------------
	// The following are for testing/debugging

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		String NEW_LINE = System.getProperty("line.separator");

		result.append(this.getClass().getName() + " Object {" + NEW_LINE);
		for (Frame f : this.frame) {
			result.append(" Frame:" + NEW_LINE)
					.append("  Filename: " + f.filename + NEW_LINE)
					.append("  " + f.hotspot.toString() + NEW_LINE)
					.append("  " + f.toString() + NEW_LINE);
		}
		result.append("}");

		return result.toString();
	}
/*
	public static final void main(String[] args) {
		if(args.length < 2) {
			System.err.println("Usage: Unzip zipfile racename");
			System.exit(1);
		}

		try {
			String zipfile = args[0];
			// "enhanced" for-loop can't be used, because Java doesn't do
			// array slices easily
			for (int i = 1; i < args.length; i++) {
				Content c = new Content(zipfile, args[i]);
				System.err.println(c.toString());
			}
		} catch (IOException ioe) {
			System.err.println("Unhandled exception:");
			ioe.printStackTrace();
			System.exit(1);
		}
	}
*/

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
		Frame(ZipFile z, String def)
		throws IOException {
			String[] field = def.trim().split("\\s+", 5);
			this.filename = field[0];
			this.hotspot = new Hotspot(field[3], field[4]);
			try {
				byte[] input = readFromContentPack(z, this.filename);
				this.content = BitmapFactory.decodeByteArray(input, 0, input.length);
				if (this.content == null) {
					throw new IOException("Could not decode file " + this.filename);
				}
			}
			catch (IOException ioe) {
				// TODO: need to figure out where to handle problems w/ content
				throw ioe;
			}
		}
		@Override
		public String toString() {
			return String.format("Content: %dx%d", this.content.getWidth(),
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
				return;
			}
			Hotspot(int x, int y) {
				this((float) x, (float) y);
			}
			Hotspot(String x, String y) {
				this((int) Integer.parseInt(x), (int) Integer.parseInt(y));
			}
			Hotspot(int x, String y) {
				this(x, (int) Integer.parseInt(y));
			}
			Hotspot(String x, int y) {
				this((int) Integer.parseInt(x), y);
			}
			@Override
			public String toString() {
				return String.format("Hotspot: (%1.2f, %1.2f)", this.x, this.y);
			}
		}
		// END Content.Frame.Hotspot
		//------------------------------------------------------------------------

	}
	// END Content.Frame
	//------------------------------------------------------------------------
}
