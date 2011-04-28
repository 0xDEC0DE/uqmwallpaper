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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.widget.TextView;

public class Settings
extends PreferenceActivity
implements SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceClickListener
{
	private static final String TAG = "UQMWallpaper.Settings";
	private static final String CONTENT_PREF = "contentPack";
	private static final String ALIEN_RACE = "race";
	private static final String SCALING = "scaling";
	private Preference mContent;
	private ListPreference mAlien;
	private ListPreference mScaling;

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		getPreferenceManager().setSharedPreferencesName(UQMWallpaper.SHARED_PREFS_NAME);
		addPreferencesFromResource(R.xml.settings);
		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

		prefs.registerOnSharedPreferenceChangeListener(this);

		mContent = (Preference) findPreference(CONTENT_PREF);
		mAlien = (ListPreference) findPreference(ALIEN_RACE);
		mScaling = (ListPreference) findPreference(SCALING);

		String buf;
		if ((buf = prefs.getString(CONTENT_PREF, null)) != null)
			mContent.setSummary(buf);
		else
			mAlien.setEnabled(false);

		if ((buf = prefs.getString(ALIEN_RACE, null)) != null)
			mAlien.setSummary(get_by_value(mAlien, buf));

		if ((buf = prefs.getString(SCALING, null)) != null)
			mScaling.setSummary(get_by_value(mScaling, buf));

		mContent.setOnPreferenceClickListener(this);
		mAlien.setOnPreferenceClickListener(this);
		mScaling.setOnPreferenceClickListener(this);

		return;
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		// If they've picked anything other than the content pack, bug out and
		// go back to the preview screen

		String buf = prefs.getString(key, null);

		if (key.equals(CONTENT_PREF)) {
			if (buf != null) {
				mContent.setSummary(buf);
				mAlien.setEnabled(true);
			} else {
				mAlien.setEnabled(false);
			}
			return;
		}
		else if (key.equals(ALIEN_RACE)) {
			if (buf != null)
				mAlien.setSummary(get_by_value(mAlien, buf));
		}
		else if (key.equals(SCALING)) {
			if (buf != null)
				mScaling.setSummary(get_by_value(mScaling, buf));
		}
		this.finish();
		return;
	}

	// Intercepts the clicks off of the preference menu, optionally firing
	// off Activity methods to complete the tasks.  Since Android doesn't
	// really have the concept of "modal dialogs", if you want that kind
	// of functionality, you need to organize Activities and Listeners to
	// make it happen, which makes the program rather difficult to read.
	// In this case, clicking the CONTENT_PREF menu should bring up an
	// alert dialog with a hyperlink in it (that the user can
	// click-through) and after they click "OK", it presents the menu
	@Override
	public boolean onPreferenceClick(Preference pref) {
		final Intent i = new Intent(this, FileChooser.class);

		// Cargo-cult programming: all the examples I saw for handling
		// clicks used anonymous sub-classes to do it.  So here you go
		OnClickListener okButton = new OnClickListener() {
			public void onClick(DialogInterface arg0, int arg1) {
				try {
					startActivityForResult(i, 0);
				} catch (ActivityNotFoundException e) {
					Log.w(TAG, e.getMessage());
				}
			}
		};

		if(pref.getKey().equals(CONTENT_PREF)) {
			// More cargo-cult programming: this is how the Internet told
			// me to get clickable links in AlertDialogs
			final SpannableString s = new SpannableString(this.getText(R.string.fetch_content));
			Linkify.addLinks(s, Linkify.WEB_URLS);
			final AlertDialog d = new AlertDialog.Builder(this)
					.setTitle(this.getText(R.string.fetch_content_hdr))
					.setMessage(s)
					.setPositiveButton(android.R.string.ok, okButton)
					.show();
			((TextView)d.findViewById(android.R.id.message))
					.setMovementMethod(LinkMovementMethod.getInstance());

			return(true);
		}
		else
			return(false);
	}

	// Validates the results of onPreferenceClick() and saves the results
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if((resultCode == Activity.RESULT_OK) && (data != null)) {
			String path = uriToFilePath(getBaseContext(), data.toUri(0));
			if(path != null){
				findPreference(CONTENT_PREF).getEditor().putString(CONTENT_PREF, path).commit();
				mAlien.setEnabled(true);
			}
		}
		return;
	}

	// given a content or file uri, return a file path
	private static String uriToFilePath(Context context, String contentUri) {
		if(Uri.parse(contentUri).getScheme().equals("content")){
			String[] p={MediaStore.MediaColumns.DATA};
			Cursor cursor = context.getContentResolver().query(
															   Uri.parse(contentUri),
															   p, // which columns
															   null, // which rows (all rows)
															   null, // selection args (none)
															   null); // order-by clause (ascending by name)
			if(cursor != null){
				int iColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
				if(cursor.moveToFirst()){
					return(cursor.getString(iColumn));
				}
			}
		}
		if(Uri.parse(contentUri).getScheme().equals("file")){
			return(Uri.parse(contentUri).getPath());
		}
		return(null);
	}

	private String get_by_value(ListPreference l, String buf) {
		return (String) l.getEntries()[l.findIndexOfValue(buf)];
	}
}
