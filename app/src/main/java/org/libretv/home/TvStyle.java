package org.libretv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** 电视端统一视觉与短动效，尊重系统关闭动画的设置。 */
final class TvStyle {
    static final int BG=Color.rgb(14,15,19), PANEL=Color.rgb(29,30,36), TEXT=Color.rgb(245,244,239);
    static final int MUTED=Color.rgb(157,158,169), ACCENT=Color.rgb(220,247,99), LAVENDER=Color.rgb(177,163,245);
    static int dp(Context c,float value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int color,int border,float radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius));
        d.setStroke(dp(c,1),border); return d;
    }
    static boolean animated(Context c) { return Settings.Global.getFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0; }
    static void focus(View view,boolean card) {
        Context c=view.getContext(); view.setFocusable(true);
        GradientDrawable surface=shape(c,PANEL,Color.rgb(48,49,57),card?16:14); view.setBackground(surface);
        boolean textButton=view instanceof android.widget.Button;
        view.setOnFocusChangeListener((v,on) -> {
            v.animate().cancel(); v.setAlpha(1f);
            surface.setColor(on && textButton?ACCENT:PANEL);
            surface.setStroke(dp(c,on?2:1),on?ACCENT:Color.rgb(48,49,57));
            if(textButton) ((TextView)v).setTextColor(on?BG:TEXT);
            float scale=on?(card?1.035f:textButton?1.025f:1f):1f;
            float lift=on && card?-dp(c,3):0;
            if(animated(c)) v.animate().setStartDelay(0).scaleX(scale).scaleY(scale).translationY(lift)
                .translationZ(on?dp(c,12):0).setDuration(on?260:180)
                .setInterpolator(on?new OvershootInterpolator(.7f):new DecelerateInterpolator()).start();
            else { v.setScaleX(scale); v.setScaleY(scale); v.setTranslationY(lift); v.setTranslationZ(on?dp(c,12):0); }
        });
    }
    static void enter(View view,int delay) {
        if(!animated(view.getContext())) return;
        view.setAlpha(0f); view.setTranslationY(dp(view.getContext(),12));
        view.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(320).setInterpolator(new DecelerateInterpolator(1.7f)).start();
    }
    static void reveal(View view) {
        if(!animated(view.getContext())) return;
        view.setAlpha(0f); view.animate().alpha(1).setStartDelay(0).setDuration(260).start();
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
    /** 单个画布绘制一排骨架，只在等待首批数据且可见时运行。 */
    static final class Skeleton extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect=new RectF();
        private final Matrix sweepMatrix=new Matrix();
        private final LinearGradient sweep=new LinearGradient(-1,0,1,0,new int[]{0xFF292A32,0xFF3B3B48,0xFF292A32},null,Shader.TileMode.CLAMP);
        private ValueAnimator shimmer;
        private float phase;
        Skeleton(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if(animated(getContext())) {
                shimmer=ValueAnimator.ofFloat(-1f,2f); shimmer.setDuration(1500); shimmer.setRepeatCount(ValueAnimator.INFINITE);
                shimmer.addUpdateListener(a -> { phase=(float)a.getAnimatedValue(); invalidate(); }); shimmer.start();
            }
        }
        @Override protected void onDetachedFromWindow() { if(shimmer!=null) { shimmer.cancel(); shimmer=null; } super.onDetachedFromWindow(); }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if(shimmer!=null) { if(visibility==VISIBLE) shimmer.resume(); else shimmer.pause(); }
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float gap=dp(getContext(),14), cell=(getWidth()-gap*4)/5f;
            float inset=dp(getContext(),8), poster=Math.min((cell-inset*2)*1.4f,getHeight()-dp(getContext(),77));
            float center=phase*getWidth(), band=getWidth()*.35f;
            sweepMatrix.setScale(band,1); sweepMatrix.postTranslate(center,0); sweep.setLocalMatrix(sweepMatrix);
            for(int i=0;i<5;i++) {
                float x=i*(cell+gap); paint.setShader(null); paint.setColor(PANEL);
                rect.set(x,0,x+cell,getHeight()-dp(getContext(),8)); canvas.drawRoundRect(rect,dp(getContext(),16),dp(getContext(),16),paint);
                paint.setShader(sweep);
                rect.set(x+inset,inset,x+cell-inset,inset+poster); canvas.drawRoundRect(rect,dp(getContext(),10),dp(getContext(),10),paint);
                paint.setShader(null); paint.setColor(0xFF34353F);
                rect.set(x+inset,poster+dp(getContext(),20),x+cell*.8f,poster+dp(getContext(),32)); canvas.drawRoundRect(rect,5,5,paint);
                rect.set(x+inset,poster+dp(getContext(),42),x+cell*.6f,poster+dp(getContext(),50)); canvas.drawRoundRect(rect,4,4,paint);
            }
        }
    }
    private TvStyle() {}
}
