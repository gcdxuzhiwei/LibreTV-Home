package org.libretv.home;

import android.os.Bundle;
import android.view.View;
import com.facebook.react.ReactActivity;

public final class TvActivity extends ReactActivity {
    @Override protected String getMainComponentName() { return "LibreTVHome"; }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
