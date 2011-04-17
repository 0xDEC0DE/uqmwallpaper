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
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileChooser extends ListActivity {

	private enum DISPLAYMODE { ABSOLUTE, RELATIVE; }

	private final DISPLAYMODE displayMode = DISPLAYMODE.RELATIVE;
	private List<String> directoryEntries = new ArrayList<String>();
	private File currentDirectory = new File("/");

	// no need to call setContentView() here, everything auto-vivifies later
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		browseTo(new File("/sdcard"));
	}

	private void upOneLevel() {
		if (this.currentDirectory.getParent() != null)
			this.browseTo(this.currentDirectory.getParentFile());
	}

	private void browseTo(final File aDirectory) {
		OnClickListener okButton = new OnClickListener() {
			public void onClick(DialogInterface arg0, int arg1) {
				return;
			}
		};
		File old = this.currentDirectory;

		if (aDirectory.isDirectory()) {
			this.currentDirectory = aDirectory;
			try {
				fill(aDirectory.listFiles());
			}
			catch (Exception e) {
				new AlertDialog.Builder(this)
				.setTitle("Error")
				.setMessage("Cannot browse to directory\n" +
							aDirectory.getAbsolutePath())
				.setPositiveButton(android.R.string.ok, okButton)
				.show();
				this.currentDirectory = old;
				fill(old.listFiles());
			}
		} else {
			try {
				Content c = new Content(aDirectory.getAbsolutePath());
				this.setResult(Activity.RESULT_OK, new Intent(Intent.ACTION_INSERT, Uri.fromFile(aDirectory)));
				this.finish();
			} catch (IOException ioe) {
				new AlertDialog.Builder(this)
					.setTitle("Error")
					.setMessage("File\n" +
								aDirectory.getAbsolutePath() +
								"\nis not a valid content pack")
					.setPositiveButton(android.R.string.ok, okButton)
					.show();
			}
		}
	}

	private void fill(File[] files) {
		this.directoryEntries.clear();

		try {
			Thread.sleep(10);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		this.directoryEntries.add(".");

		if(this.currentDirectory.getParent() != null)
			this.directoryEntries.add("..");

		switch(this.displayMode){
			case ABSOLUTE:
				for (File file : files){
					this.directoryEntries.add(file.getPath());
				}
				break;
			case RELATIVE:
				for (File file : files){
					this.directoryEntries.add(file.getName());
				}
				break;
		}

		ArrayAdapter<String> directoryList = new ArrayAdapter<String>(this,
				R.layout.file_row, R.id.text, this.directoryEntries);

		this.setListAdapter(directoryList);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		String selectedFileString = this.directoryEntries.get(position);

		if (selectedFileString.equals(".")) {
			this.browseTo(this.currentDirectory); // Refresh
		} else if(selectedFileString.equals("..")){
			this.upOneLevel();
		} else {
			File clickedFile = null;
			switch(this.displayMode){
				case RELATIVE:
					clickedFile = new File(this.currentDirectory.getAbsolutePath()
										   + "/"
										   + this.directoryEntries.get(position));
					break;
				case ABSOLUTE:
					clickedFile = new File(this.directoryEntries.get(position));
					break;
			}
			if(clickedFile != null)
				this.browseTo(clickedFile);
		}
	}
}
