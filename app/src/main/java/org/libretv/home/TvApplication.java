package org.libretv.home;

import android.app.Application;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.JSEngineResolutionAlgorithm;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;
import java.util.Arrays;
import java.util.List;

public final class TvApplication extends Application implements ReactApplication {
    private final ReactNativeHost host = new ReactNativeHost(this) {
        @Override public boolean getUseDeveloperSupport() { return false; }
        @Override protected List<ReactPackage> getPackages() {
            return Arrays.asList(new MainReactPackage(), new LibreTvPackage());
        }
        @Override protected String getJSMainModuleName() { return "index"; }
        @Override protected JSEngineResolutionAlgorithm getJSEngineResolutionAlgorithm() { return JSEngineResolutionAlgorithm.HERMES; }
    };
    @Override public ReactNativeHost getReactNativeHost() { return host; }
    @Override public void onCreate() { super.onCreate(); SoLoader.init(this, false); }
}
