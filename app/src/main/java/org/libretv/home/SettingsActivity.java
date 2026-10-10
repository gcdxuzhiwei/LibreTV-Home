package org.libretv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 原生影视源管理；浏览、详情和观看记录由 React Native TV 提供。 */
public final class SettingsActivity extends Activity {
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private LocalStore store;
    private List<Catalog.Source> sources;
    private LinearLayout body;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        store = new LocalStore(this);
        sources = store.sources();
        settings();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setTextColor(TvStyle.TEXT);
        b.setTypeface(null,Typeface.BOLD); b.setStateListAnimator(null);
        b.setPadding(dp(16),dp(4),dp(16),dp(4)); b.setMinHeight(dp(44)); TvStyle.focus(b); b.setOnClickListener(v -> TvStyle.press(b,action));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,dp(44)); lp.setMargins(0,0,dp(10),0); b.setLayoutParams(lp); return b;
    }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setClipChildren(false); return r; }
    private void shell(String subtitle) {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(36),dp(20),dp(36),dp(16)); root.setClipChildren(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(29,26,40),TvStyle.BG,TvStyle.BG}));
        LinearLayout header = row(); header.setClipChildren(false); TextView logo = text("LibreTV.",27,TvStyle.TEXT); logo.setTypeface(null,Typeface.BOLD);
        header.addView(logo); TextView sub = text("   /   " + subtitle,14,TvStyle.MUTED); header.addView(sub,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(header);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(0,dp(16),0,0); body.setClipChildren(false);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root); TvStyle.enter(body,0);
    }
    private void settings() {
        shell("影视源管理");
        TextView help=text("管理你的影视来源。启用的来源参与搜索，首页可单独选择浏览来源。",15,TvStyle.MUTED); body.addView(help);
        LinearLayout actions=row(); actions.setPadding(0,dp(14),0,dp(14)); actions.addView(button("返回首页",this::finish));
        actions.addView(button("添加来源",this::addSource)); actions.addView(button("关于",() -> new AlertDialog.Builder(this).setTitle("LibreTV 家庭影院 " + BuildConfig.VERSION_NAME).setMessage("基于 LibreSpark/LibreTV 的苹果 CMS 协议，React Native TV 界面与原生播放器。\n\n无账号、无启动密码、无自建后端。数据与视频由第三方源直接提供，观看记录仅保存在本机。\n\n上游：https://github.com/LibreSpark/LibreTV\n许可：GNU AGPL-3.0，源码随项目提供。\n播放器：AndroidX Media3（Apache-2.0）。").setPositiveButton("关闭",null).show())); body.addView(actions);
        ScrollView sc=new ScrollView(this); TvStyle.viewport(sc); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        for(Catalog.Source s : sources) {
            LinearLayout r=row(); CheckBox toggle=new CheckBox(this); toggle.setText(s.name); toggle.setTextSize(17); toggle.setTextColor(Color.WHITE); toggle.setChecked(s.enabled); toggle.setOnCheckedChangeListener((b,on) -> { s.enabled=on; store.saveSources(sources); }); r.addView(toggle,new LinearLayout.LayoutParams(0,dp(52),1));
            r.addView(button("检测",() -> probe(s))); r.addView(button("删除",() -> new AlertDialog.Builder(this).setTitle("删除“"+s.name+"”？").setPositiveButton("删除",(d,w) -> { sources.remove(s); store.saveSources(sources); settings(); }).setNegativeButton("取消",null).show()));
            list.addView(r); TextView url=text(s.url,12,Color.GRAY); url.setPadding(dp(8),0,0,dp(14)); list.addView(url);
        }
        sc.addView(list); body.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); actions.getChildAt(0).requestFocusFromTouch();
    }
    private void probe(Catalog.Source s) {
        toast("正在检测 "+s.name); workers.submit(() -> { String message; try { Catalog.Page p=Catalog.page(s,"","",1); message=s.name+" 可用，返回 "+p.videos.size()+" 部影视"; } catch(Exception e) { message=s.name+"："+Network.describe(e); } String result=message; runOnUiThread(() -> { if(!isDestroyed()) new AlertDialog.Builder(this).setTitle("来源检测").setMessage(result).setPositiveButton("确定",null).show(); }); });
    }
    private void addSource() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(24),dp(12),dp(24),0);
        EditText name=new EditText(this); name.setHint("来源名称"); name.setSingleLine(); form.addView(name);
        EditText url=new EditText(this); url.setHint("http(s)://…/api.php/provide/vod/"); url.setSingleLine(); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI); form.addView(url);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加苹果 CMS JSON 接口").setView(form).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n=name.getText().toString().trim(), u=url.getText().toString().trim();
            if(n.isEmpty() || !Catalog.http(u)) { toast("填写名称和有效的 http(s) 接口地址"); return; }
            for(Catalog.Source s:sources) if(s.url.equals(u)) { toast("该地址已存在"); return; }
            sources.add(new Catalog.Source(n,u,true)); store.saveSources(sources); dialog.dismiss(); settings();
        })); dialog.show();
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { workers.shutdownNow(); super.onDestroy(); }
}
