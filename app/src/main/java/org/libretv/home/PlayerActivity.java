package org.libretv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private Catalog.Video video;
    private List<Catalog.Line> lines;
    private LocalStore store;
    private ExoPlayer player;
    private PlayerView view;
    private TextView title, message;
    private LinearLayout toolbar;
    private Button previous, next;
    private View toolbarFocus;
    private int line, episode;
    private int shortcutKey=KeyEvent.KEYCODE_UNKNOWN;
    private long position;
    private boolean playWhenReady=true;
    private AlertDialog errorDialog;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable checkpoint=new Runnable() { @Override public void run() { save(); handler.postDelayed(this,5000); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new LocalStore(this);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try {
            try(InputStream in=openFileInput("playback.json"); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                video=Catalog.Video.from(new JSONObject(out.toString(StandardCharsets.UTF_8.name())));
            }
            lines=Catalog.lines(video); if(lines.isEmpty()) throw new Exception("没有可播放线路");
            line=Math.max(0,Math.min(getIntent().getIntExtra("line",0),lines.size()-1));
            episode=Math.max(0,Math.min(getIntent().getIntExtra("episode",0),lines.get(line).episodes.size()-1));
            JSONObject progress=store.progress(video);
            if(getIntent().getBooleanExtra("resume",false) && lines.get(line).name.equals(progress.optString("line")) && episode==progress.optInt("episode",-1)) {
                position=progress.optLong("position",0); long duration=progress.optLong("duration",0);
                if(duration>0 && duration-position<15000) position=0;
            }
            if(state!=null) { line=state.getInt("line",line); episode=state.getInt("episode",episode); position=state.getLong("position",position); playWhenReady=state.getBoolean("playing",true); }
            layout();
        } catch(Exception e) { Toast.makeText(this,"打开失败："+e.getMessage(),Toast.LENGTH_LONG).show(); finish(); }
    }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private Button button(String label,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(TvStyle.TEXT);
        b.setTypeface(null,android.graphics.Typeface.BOLD); b.setStateListAnimator(null); TvStyle.focus(b);
        b.setOnClickListener(v -> TvStyle.press(b,action));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42)); lp.setMargins(dp(6),0,0,0); b.setLayoutParams(lp); return b;
    }
    private void layout() {
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        view=new PlayerView(this); view.setUseController(true); view.setControllerShowTimeoutMs(4500); view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        view.setShowPreviousButton(false); view.setShowNextButton(false);
        view.findViewById(androidx.media3.ui.R.id.exo_settings).setVisibility(View.GONE);
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        toolbar=new LinearLayout(this); toolbar.setClipChildren(false); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(24),dp(12),dp(24),dp(12)); toolbar.setBackgroundColor(0xE00E0F13);
        title=new TextView(this); title.setTextColor(TvStyle.TEXT); title.setTextSize(17); title.setMaxLines(2); toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(button("退出",this::finish)); toolbar.addView(button("选集",this::episodes)); toolbar.addView(button("线路",this::chooseLine));
        previous=button("上一集",() -> switchEpisode(episode-1)); next=button("下一集",() -> switchEpisode(episode+1)); toolbar.addView(previous); toolbar.addView(next);
        root.addView(toolbar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        message=new TextView(this); message.setTextColor(TvStyle.TEXT); message.setTextSize(17); message.setGravity(Gravity.CENTER); message.setPadding(dp(20),dp(12),dp(20),dp(12)); message.setBackgroundColor(0xDB1D1E24); message.setVisibility(View.GONE);
        root.addView(message,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> TvStyle.showPanel(toolbar,visibility==View.VISIBLE));
        setContentView(root); updateTitle();
    }
    private void startPlayer() {
        OkHttpDataSource.Factory http=new OkHttpDataSource.Factory(Network.CLIENT).setUserAgent(Catalog.UA);
        player=new ExoPlayer.Builder(this).setRenderersFactory(new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setSeekBackIncrementMs(15000).setSeekForwardIncrementMs(15000)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();
        view.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if(state==Player.STATE_READY) { message.setVisibility(View.GONE); save(); }
                if(state==Player.STATE_ENDED) { if(episode+1<lines.get(line).episodes.size()) switchEpisode(episode+1); else { save(); view.showController(); } }
            }
            @Override public void onPlayerError(PlaybackException error) {
                message.setText("播放失败，可重试或选择其他线路 / 来源"); message.setVisibility(View.VISIBLE); view.showController();
                if(isFinishing() || errorDialog!=null) return;
                errorDialog=new AlertDialog.Builder(PlayerActivity.this).setTitle("无法播放此视频")
                    .setMessage("错误："+error.getErrorCodeName()+"\n"+Network.describe(error)+"\n设备：Android "+android.os.Build.VERSION.RELEASE+" / "+android.os.Build.MODEL+"\n可重试、切换线路，或返回详情搜索其他来源。")
                    .setPositiveButton("重试",(d,w) -> { player.prepare(); player.play(); })
                    .setNeutralButton("切换线路",(d,w) -> chooseLine()).setNegativeButton("关闭",null).create();
                errorDialog.setOnDismissListener(d -> errorDialog=null); errorDialog.show();
            }
        });
        prepare(); handler.postDelayed(checkpoint,5000);
    }
    private void prepare() {
        Catalog.Episode ep=lines.get(line).episodes.get(episode); message.setVisibility(View.GONE);
        player.setMediaItem(MediaItem.fromUri(ep.url)); player.seekTo(position); player.prepare(); player.setPlayWhenReady(playWhenReady);
        updateTitle(); view.setControllerShowTimeoutMs(4500); view.showController(); view.requestFocus();
    }
    private void updateTitle() {
        title.setText(video.title()+"  ·  "+lines.get(line).episodes.get(episode).name+"\n"+video.source.name+" / "+lines.get(line).name+"  ·  左右跳转15秒，上下切换焦点，确定暂停");
        previous.setEnabled(episode>0); next.setEnabled(episode+1<lines.get(line).episodes.size());
    }
    private void switchEpisode(int index) {
        if(player==null || index<0 || index>=lines.get(line).episodes.size()) return;
        save(); episode=index; position=0; playWhenReady=true; prepare();
    }
    private void episodes() {
        String[] names=new String[lines.get(line).episodes.size()]; for(int i=0;i<names.length;i++) names[i]=lines.get(line).episodes.get(i).name;
        new AlertDialog.Builder(this).setTitle("选集").setSingleChoiceItems(names,episode,(d,i) -> { d.dismiss(); switchEpisode(i); }).setNegativeButton("关闭",null).show();
    }
    private void chooseLine() {
        String[] names=new String[lines.size()]; for(int i=0;i<names.length;i++) names[i]=lines.get(i).name;
        new AlertDialog.Builder(this).setTitle("切换线路（保留当前进度）").setSingleChoiceItems(names,line,(d,i) -> {
            if(player!=null) {
                save(); String currentName=lines.get(line).episodes.get(episode).name; position=player.getCurrentPosition();
                int target=-1; for(int k=0;k<lines.get(i).episodes.size();k++) if(lines.get(i).episodes.get(k).name.equals(currentName)) { target=k; break; }
                if(target<0) { target=Math.min(episode,lines.get(i).episodes.size()-1); position=0; }
                line=i; episode=target; playWhenReady=true; prepare();
            } d.dismiss();
        }).setNegativeButton("关闭",null).show();
    }
    private void save() {
        if(player!=null && video!=null) {
            position=player.getCurrentPosition(); playWhenReady=player.getPlayWhenReady();
            store.saveProgress(video,lines.get(line).name,episode,position,player.getDuration());
        }
    }
    @Override protected void onStart() { super.onStart(); if(video!=null && view!=null && player==null) startPlayer(); }
    @Override protected void onStop() {
        shortcutKey=KeyEvent.KEYCODE_UNKNOWN;
        handler.removeCallbacks(checkpoint); save();
        if(player!=null) { view.setPlayer(null); player.release(); player=null; }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) { save(); super.onSaveInstanceState(out); out.putInt("line",line); out.putInt("episode",episode); out.putLong("position",position); out.putBoolean("playing",playWhenReady); }
    @Override public boolean onKeyDown(int keyCode,KeyEvent event) {
        if(player==null) return super.onKeyDown(keyCode,event);
        if(keyCode==KeyEvent.KEYCODE_MENU) { episodes(); return true; }
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if(player.isPlaying()) player.pause(); else player.play(); return true; }
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY) { player.play(); return true; }
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PAUSE) { player.pause(); return true; }
        return super.onKeyDown(keyCode,event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode=event.getKeyCode();
        // 快捷键的抬起和长按重复事件也需消费，避免控制条显示后再次触发控件。
        if(shortcutKey!=KeyEvent.KEYCODE_UNKNOWN && keyCode==shortcutKey) {
            if(event.getAction()==KeyEvent.ACTION_UP) shortcutKey=KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if(player!=null && event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
            boolean inToolbar=toolbar.hasFocus() && toolbar.getVisibility()==View.VISIBLE;
            // 播放区域的方向快捷键不依赖控制条是否显示，顶部仍保留横向按钮导航。
            if(keyCode==KeyEvent.KEYCODE_DPAD_UP || keyCode==KeyEvent.KEYCODE_DPAD_DOWN) {
                shortcutKey=keyCode;
                if(keyCode==KeyEvent.KEYCODE_DPAD_UP) {
                    view.setControllerShowTimeoutMs(0); view.showController();
                    if(!inToolbar) {
                        if(toolbarFocus==null || !toolbarFocus.isEnabled()) toolbarFocus=toolbar.getChildAt(1);
                        toolbarFocus.requestFocus();
                    }
                } else {
                    if(inToolbar) toolbarFocus=getCurrentFocus();
                    view.setControllerShowTimeoutMs(4500); view.showController();
                    view.findViewById(androidx.media3.ui.R.id.exo_play_pause).requestFocus();
                }
                return true;
            }
            if(!inToolbar && (keyCode==KeyEvent.KEYCODE_DPAD_LEFT || keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)) {
                shortcutKey=keyCode;
                if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT) player.seekBack(); else player.seekForward();
                view.showController(); return true;
            }
            if(!inToolbar && (keyCode==KeyEvent.KEYCODE_DPAD_CENTER || keyCode==KeyEvent.KEYCODE_ENTER)) { shortcutKey=keyCode; if(player.getPlayWhenReady()) player.pause(); else player.play(); view.showController(); return true; }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() {
        if(view!=null && view.isControllerFullyVisible()) {
            view.setControllerShowTimeoutMs(4500); view.hideController(); view.requestFocus();
        } else finish();
    }
}
