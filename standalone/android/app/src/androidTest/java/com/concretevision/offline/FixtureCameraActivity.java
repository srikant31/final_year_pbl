package com.concretevision.offline;

import android.app.Activity;
import android.os.Bundle;
import android.provider.MediaStore;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;

/** Test APK only, different UID: exercises the real temporary URI write grant. No sensor access. */
public final class FixtureCameraActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("fixture_mode");
        if ("cancel".equals(mode)) { setResult(RESULT_CANCELED); finish(); return; }
        try {
            Uri output = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            if (!"empty".equals(mode)) {
                try (InputStream input = getAssets().open("honeycomb.jpg");
                     OutputStream destination = getContentResolver().openOutputStream(output,"w")) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) >= 0) destination.write(buffer,0,count);
                }
            }
            setResult(RESULT_OK); // No thumbnail/data Intent: the full image is in EXTRA_OUTPUT.
        } catch (Exception error) { setResult(RESULT_CANCELED); }
        finish();
    }
}
