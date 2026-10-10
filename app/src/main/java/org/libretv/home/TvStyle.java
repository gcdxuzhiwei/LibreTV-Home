package org.libretv.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** 电视端统一视觉与短动效，尊重系统关闭动画的设置。 */
final class TvStyle {
    static final int BG=Color.rgb(14,15,19), PANEL=Color.rgb(29,30,36), TEXT=Color.rgb(245,244,239);
    static final int MUTED=Color.rgb(157,158,169), ACCENT=Color.rgb(220,247,99);
    static int dp(Context c,float value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int color,int border,float radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius));
        d.setStroke(dp(c,1),border); return d;
    }
    static boolean animated(Context c) { return Settings.Global.getFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0; }
    static void focus(View view) {
        Context c=view.getContext(); view.setFocusable(true);
        GradientDrawable surface=shape(c,PANEL,Color.rgb(48,49,57),14); view.setBackground(surface);
        boolean textButton=view instanceof android.widget.Button;
        view.setOnFocusChangeListener((v,on) -> {
            v.animate().cancel(); v.setAlpha(1f);
            surface.setColor(on && textButton?ACCENT:PANEL);
            surface.setStroke(dp(c,on?2:1),on?ACCENT:Color.rgb(48,49,57));
            if(textButton) ((TextView)v).setTextColor(on?BG:TEXT);
            float scale=on && textButton?1.025f:1f;
            if(animated(c)) v.animate().setStartDelay(0).scaleX(scale).scaleY(scale)
                .translationZ(on?dp(c,12):0).setDuration(on?260:180)
                .setInterpolator(on?new OvershootInterpolator(.7f):new DecelerateInterpolator()).start();
            else { v.setScaleX(scale); v.setScaleY(scale); v.setTranslationZ(on?dp(c,12):0); }
        });
    }
    static void enter(View view,int delay) {
        if(!animated(view.getContext())) return;
        view.setAlpha(0f); view.setTranslationY(dp(view.getContext(),12));
        view.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(320).setInterpolator(new DecelerateInterpolator(1.7f)).start();
    }
    static void viewport(View view) {
        // 焦点卡片可在列表内部上浮，但不能越过视口覆盖搜索区。
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v,Outline outline) { outline.setRect(0,0,v.getWidth(),v.getHeight()); }
        });
        view.setClipToOutline(true);
    }
    static void press(View view,Runnable action) {
        if(!animated(view.getContext())) { action.run(); return; }
        view.animate().cancel(); view.setAlpha(1);
        view.animate().setStartDelay(0).scaleX(.97f).scaleY(.97f).setDuration(75)
            .withEndAction(() -> {
                if(view.isAttachedToWindow()) { view.animate().scaleX(view.hasFocus()?1.025f:1f).scaleY(view.hasFocus()?1.025f:1f).setDuration(160).start(); action.run(); }
            }).start();
    }
    static void showPanel(View panel,boolean show) {
        panel.animate().cancel();
        if(show) {
            panel.setVisibility(View.VISIBLE); enter(panel,0);
        } else if(animated(panel.getContext()) && panel.isShown()) {
            panel.animate().setStartDelay(0).alpha(0f).translationY(-dp(panel.getContext(),8)).setDuration(160)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
        } else panel.setVisibility(View.GONE);
    }
    private TvStyle() {}
}
