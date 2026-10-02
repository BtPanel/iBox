package com.media.ibox.ui.activity;

import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

/** Empty debug window. Fixtures and assertions live in androidTest. */
public final class UiSurfaceTestActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        if (getPackageName().endsWith(".tv")) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        super.onCreate(savedInstanceState);
    }
}
