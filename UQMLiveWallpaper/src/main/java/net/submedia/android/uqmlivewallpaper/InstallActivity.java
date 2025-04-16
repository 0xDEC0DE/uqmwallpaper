/*
 * Copyright (C) 2025 Nicolas Simonds
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

package net.submedia.android.uqmlivewallpaper;

import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class InstallActivity extends ComponentActivity {

    private final ActivityResultLauncher<Intent> wallpaperLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> finish()
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) launchWallpaperPicker();
    }

    protected void launchWallpaperPicker() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, UQMWallpaper.class));
        try {
            wallpaperLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            try {
                Intent chooserIntent = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
                wallpaperLauncher.launch(chooserIntent);
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, R.string.unsupported_device, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
