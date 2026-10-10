package org.libretv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Html;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {
    private static final int BG=TvStyle.BG, PANEL=TvStyle.PANEL, ACCENT=TvStyle.ACCENT;
    private final ExecutorService workers = Executors.newFixedThreadPool(6);
    private final ThreadPoolExecutor images = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
    private final List<Future<?>> pending = new ArrayList<>();
    private final List<Future<?>> pendingImages = new ArrayList<>();
    private volatile int imageGeneration;
    private final List<Catalog.Video> videos = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final Map<String,Integer> pageCounts = new HashMap<>();
    private final Set<Catalog.Source> incompletePageSources = new LinkedHashSet<>();
    private final List<String[]> types = new ArrayList<>();
    private final LruCache<String,Bitmap> covers = new LruCache<String,Bitmap>(12*1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount()/1024; }
    };
    private LocalStore store;
    private List<Catalog.Source> sources;
    private Catalog.Source selected;
    private LinearLayout root, body;
    private TextView status;
    private EditText search;
    private ScrollView scroll;
    private GridLayout grid;
    private TvStyle.Skeleton skeleton;
    private Button more, categoryButton, sourceButton, searchButton, historyButton;
    private AlertDialog exitDialog;
    private int focusRecoveryKey=KeyEvent.KEYCODE_UNKNOWN;
    private String keyword = "", category = "", categoryName = "全部分类", screen = "home";
    private int generation, page = 1, outstanding, failures, success;
    private Catalog.Video detail;
    private List<Catalog.Line> lines = Collections.emptyList();
    private int currentLine;
    private boolean busy;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        store = new LocalStore(this); sources = store.sources(); selected = firstEnabled();
        if (saved != null) {
            keyword = saved.getString("keyword", "");
            String url = saved.getString("source", "");
            for (Catalog.Source source : sources) if (source.url.equals(url)) selected = source;
            category = saved.getString("category", ""); categoryName = saved.getString("categoryName", "全部分类");
        }
        home();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("keyword", keyword); state.putString("category", category);
        state.putString("categoryName", categoryName); if (selected != null) state.putString("source", selected.url);
    }
    private Catalog.Source firstEnabled() { for (Catalog.Source s : sources) if (s.enabled) return s; return sources.isEmpty() ? null : sources.get(0); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable box(int color, int stroke) {
        return TvStyle.shape(this,color,stroke,14);
    }
    private void focusable(View view) {
        TvStyle.focus(view,view instanceof LinearLayout);
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setTextColor(TvStyle.TEXT);
        b.setTypeface(null,Typeface.BOLD); b.setStateListAnimator(null);
        b.setPadding(dp(16),dp(4),dp(16),dp(4)); b.setMinHeight(dp(44)); focusable(b); b.setOnClickListener(v -> TvStyle.press(b,action));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,dp(44)); lp.setMargins(0,0,dp(10),0); b.setLayoutParams(lp); return b;
    }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setClipChildren(false); return r; }
    private void shell(String subtitle) {
        cancelImages();
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(36),dp(20),dp(36),dp(16)); root.setClipChildren(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(29,26,40),BG,BG}));
        LinearLayout header = row(); header.setClipChildren(false); TextView logo = text("LibreTV.",27,TvStyle.TEXT); logo.setTypeface(null,Typeface.BOLD);
        header.addView(logo); TextView sub = text("   /   " + subtitle,14,TvStyle.MUTED); header.addView(sub,new LinearLayout.LayoutParams(0,-2,1));
        historyButton=button("继续观看",this::history);
        header.addView(historyButton); header.addView(button("影视源",this::settings));
        root.addView(header);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(0,dp(16),0,0); body.setClipChildren(false);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root); TvStyle.enter(body,0);
    }
    private void home() {
        cancel();
        screen = "home"; shell("家庭影院");
        LinearLayout intro=row(); intro.setPadding(0,0,0,dp(16));
        LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        TextView headline=text("今晚，遇见好故事。",32,TvStyle.TEXT); headline.setTypeface(null,Typeface.BOLD); words.addView(headline);
        TextView tagline=text("电影 · 剧集 · 动漫    /    选一部，放松一下。",14,TvStyle.MUTED); tagline.setPadding(0,dp(6),0,0); words.addView(tagline);
        intro.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text("PRESS PLAY  ↗",13,BG); badge.setTypeface(null,Typeface.BOLD); badge.setLetterSpacing(.08f);
        badge.setPadding(dp(18),dp(12),dp(18),dp(12)); badge.setBackground(box(TvStyle.LAVENDER,0)); intro.addView(badge); body.addView(intro);
        LinearLayout controls = row();
        controls.setClipChildren(false);
        search = new EditText(this); search.setSingleLine(true); search.setText(keyword); search.setHint("搜索电影、电视剧、动漫…");
        search.setTextColor(TvStyle.TEXT); search.setTextSize(16); search.setHintTextColor(TvStyle.MUTED);
        search.setPadding(dp(16),0,dp(12),0); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH); focusable(search);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0,dp(46),1); editLp.setMargins(0,0,dp(12),0); controls.addView(search,editLp);
        searchButton=button("搜索",this::searchNow); controls.addView(searchButton);
        controls.addView(button("首页",() -> { keyword=""; category=""; categoryName="全部分类"; home(); }));
        sourceButton = button(selected == null ? "选择来源" : selected.name, this::chooseSource); controls.addView(sourceButton);
        categoryButton = button(categoryName,this::chooseCategory); controls.addView(categoryButton);
        body.addView(controls);
        search.setOnEditorActionListener((v,action,event) -> { if (action == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP)) { searchNow(); return true; } return false; });
        status = text("正在加载影视列表…",14,TvStyle.MUTED); status.setPadding(0,dp(14),0,dp(8)); body.addView(status);
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipChildren(false); scroll.setClipToPadding(false); scroll.setPadding(dp(6),0,dp(6),0); TvStyle.viewport(scroll);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setClipChildren(false);
        grid = new GridLayout(this); grid.setColumnCount(5); grid.setClipChildren(false); grid.setClipToPadding(false); grid.setPadding(0,dp(14),0,dp(6)); content.addView(grid);
        LinearLayout footer = row(); footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(16),0,dp(8));
        more=button("加载更多",() -> load(false)); footer.addView(more); footer.addView(button("重新加载",() -> load(true))); content.addView(footer);
        scroll.addView(content); body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        more.setEnabled(false);
        // 所有首页入口统一等窗口挂载后聚焦页头，再重新加载第一页。
        Button initialFocus=historyButton;
        initialFocus.post(() -> {
            if (screen.equals("home") && historyButton==initialFocus && initialFocus.isAttachedToWindow()) {
                initialFocus.requestFocusFromTouch();
                load(true);
            }
        });
    }
    private void searchNow() {
        String value = search.getText().toString().trim(); if (value.isEmpty()) { toast("请输入影视名称"); search.requestFocus(); return; }
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);
        searchButton.requestFocusFromTouch();
        keyword=value; category=""; categoryName="全部分类"; load(true);
    }
    private void cancelImages() {
        imageGeneration++;
        for (Future<?> f : pendingImages) f.cancel(true);
        pendingImages.clear();
        // 从队列移除旧页面任务，避免新页面封面排在它们后面。
        images.purge();
    }
    private void cancel() { generation++; for (Future<?> f : pending) f.cancel(true); pending.clear(); cancelImages(); busy=false; }
    private void load(boolean reset) {
        if (!reset && busy) return;
        Button resetFocus=historyButton;
        if (reset) resetFocus.requestFocusFromTouch();
        // 加载更多按钮禁用前把焦点交给末项，避免方向键兜底跳回页头。
        View paginationFocus=!reset && !videos.isEmpty() ? grid.getChildAt(grid.getChildCount()-1) : null;
        if (paginationFocus!=null) paginationFocus.requestFocusFromTouch();
        if (reset) { cancel(); videos.clear(); seen.clear(); pageCounts.clear(); incompletePageSources.clear(); grid.removeAllViews(); skeleton=null; page=1; scroll.scrollTo(0,0); }
        // 失败或离开页面时取消的来源先补齐当前页，再推进下一页。
        boolean retry = !reset && !incompletePageSources.isEmpty();
        if (!reset && !retry) page++;
        List<Catalog.Source> targets = new ArrayList<>();
        if (!keyword.isEmpty()) { for (Catalog.Source s : sources) if (s.enabled) targets.add(s); }
        else if (selected != null) targets.add(selected);
        if (retry) targets.retainAll(incompletePageSources);
        else if (!reset) {
            Iterator<Catalog.Source> iterator=targets.iterator();
            while(iterator.hasNext()) { Catalog.Source s=iterator.next(); Integer count=pageCounts.get(s.url); if(page>(count==null?1:count)) iterator.remove(); }
        }
        if (targets.isEmpty()) { status.setText(videos.isEmpty() ? "没有启用的影视源，请进入“影视源”设置。" : "已加载全部结果"); more.setEnabled(false); return; }
        int token=++generation, requestedPage=page; String requestedKeyword=keyword, requestedCategory=category;
        incompletePageSources.addAll(targets);
        outstanding=targets.size(); failures=0; success=0; busy=true; more.setEnabled(false);
        if(videos.isEmpty()) {
            skeleton=new TvStyle.Skeleton(this); GridLayout.LayoutParams placeholder=new GridLayout.LayoutParams(); placeholder.columnSpec=GridLayout.spec(0,5);
            placeholder.width=getResources().getDisplayMetrics().widthPixels-dp(84);
            placeholder.height=Math.max(dp(120),Math.min(Math.round(((placeholder.width-dp(70))/5f-dp(16))*1.4f),getResources().getDisplayMetrics().heightPixels-dp(366)))+dp(77);
            grid.addView(skeleton,placeholder);
        }
        status.setText((keyword.isEmpty()?"正在浏览 " + selected.name:"正在搜索“"+keyword+"”")+" · 第 "+page+" 页");
        for (Catalog.Source s : targets) pending.add(workers.submit(() -> {
            Catalog.Page result=null; String error=null;
            try { result=Catalog.page(s,requestedKeyword,requestedCategory,requestedPage); } catch (Exception e) { error=Network.describe(e); }
            Catalog.Page delivered=result; String message=error;
            runOnUiThread(() -> {
                if (token != generation || !screen.equals("home") || isDestroyed()) return;
                outstanding--;
                if (delivered != null) {
                    if(!delivered.videos.isEmpty()) clearSkeleton();
                    success++; pageCounts.put(s.url,delivered.pages);
                    incompletePageSources.remove(s);
                    if (requestedKeyword.isEmpty() && !delivered.categories.isEmpty()) { types.clear(); types.addAll(delivered.categories); }
                    for (Catalog.Video v : delivered.videos) if (seen.add(v.key())) { videos.add(v); card(v); }
                } else { failures++; }
                status.setText(summary() + (outstanding>0 ? " · 还有 "+outstanding+" 个源加载中" : "") + (failures>0 ? " · "+failures+" 个源失败" : ""));
                if (outstanding==0) {
                    clearSkeleton();
                    busy=false; pending.clear(); updateMore();
                    // 第一页加载完成后进入首项；用户已主动移动焦点时保留其选择。
                    if (requestedPage==1 && !videos.isEmpty() && resetFocus.hasFocus()) {
                        View firstCard=grid.getChildAt(0);
                        firstCard.post(() -> {
                            if (token==generation && screen.equals("home") && resetFocus.hasFocus()
                                && firstCard.isAttachedToWindow()) firstCard.requestFocusFromTouch();
                        });
                    }
                    if (videos.isEmpty()) status.setText(failures==0 ? "没有找到影片，请更换关键词或来源。" : "加载失败："+message+"。可重新加载或更换来源。");
                }
            });
        }));
    }
    private void updateMore() {
        boolean retry=!incompletePageSources.isEmpty(), hasNext=false;
        for (int count : pageCounts.values()) if (count>page) hasNext=true;
        more.setEnabled(!busy && (retry || hasNext));
        more.setText(retry ? "重试本页" : hasNext ? "加载更多" : "没有更多了");
    }
    private String summary() { return (keyword.isEmpty()?categoryName:"搜索“"+keyword+"”")+"  ·  "+videos.size()+" 部影视  ·  第 "+page+" 页"; }
    private void clearSkeleton() { if(skeleton!=null) { grid.removeView(skeleton); skeleton=null; } }
    private void card(Catalog.Video video) {
        int width = (getResources().getDisplayMetrics().widthPixels-dp(84))/5-dp(14);
        int posterHeight=Math.max(dp(120),Math.min(Math.round((width-dp(16))*1.4f),getResources().getDisplayMetrics().heightPixels-dp(366)));
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(8),dp(8),dp(8),dp(10)); focusable(card);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=width; lp.height=posterHeight+dp(77); lp.setMargins(0,0,dp(14),dp(16)); card.setLayoutParams(lp);
        FrameLayout poster=new FrameLayout(this); poster.setBackground(box(Color.rgb(41,39,50),0)); poster.setClipToOutline(true);
        TextView placeholder=text(video.title(),18,TvStyle.MUTED); placeholder.setGravity(Gravity.CENTER); placeholder.setPadding(dp(8),0,dp(8),0);
        poster.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); poster.addView(image,new FrameLayout.LayoutParams(-1,-1));
        card.addView(poster,new LinearLayout.LayoutParams(-1,posterHeight));
        TextView title=text(video.title(),16,TvStyle.TEXT); title.setTypeface(null,Typeface.BOLD); title.setSingleLine(); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setPadding(0,dp(10),0,0); card.addView(title);
        TextView meta=text(video.raw.optString("vod_remarks")+" · "+video.source.name,12,TvStyle.MUTED); meta.setSingleLine(); meta.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(meta);
        card.setContentDescription(video.title()+"，"+video.meta()+"，"+video.source.name);
        card.setOnClickListener(v -> TvStyle.press(card,() -> openDetail(video))); grid.addView(card);
        if(grid.getChildCount()<=10) TvStyle.enter(card,((grid.getChildCount()-1)%5)*35);
        String url=video.raw.optString("vod_pic"); if (!Catalog.http(url)) return;
        Bitmap cached=covers.get(url); if (cached!=null) { image.setImageBitmap(cached); return; }
        int imageToken=imageGeneration;
        Iterator<Future<?>> finishedImages=pendingImages.iterator();
        while(finishedImages.hasNext()) if(finishedImages.next().isDone()) finishedImages.remove();
        pendingImages.add(images.submit(() -> {
            try {
                if(imageToken!=imageGeneration || Thread.currentThread().isInterrupted()) return;
                byte[] bytes=Catalog.get(url,3*1024*1024);
                if(imageToken!=imageGeneration || Thread.currentThread().isInterrupted()) return;
                BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true; BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=1;
                while (bounds.outWidth/options.inSampleSize>400 || bounds.outHeight/options.inSampleSize>600) options.inSampleSize*=2;
                Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if (b==null) throw new Exception("图片解码失败：设备不支持此格式或服务器未返回有效图片");
                if (b!=null && imageToken==imageGeneration && !Thread.currentThread().isInterrupted()) { covers.put(url,b); runOnUiThread(() -> { if (imageToken==imageGeneration && !isDestroyed() && image.isAttachedToWindow()) { image.setImageBitmap(b); TvStyle.reveal(image); } }); }
            } catch (Exception e) {
                if(imageToken!=imageGeneration || Thread.currentThread().isInterrupted() || e instanceof InterruptedException) return;
                String reason=Network.describe(e);
                android.util.Log.w("LibreTV", "海报加载失败："+reason);
                runOnUiThread(() -> {
                    if(imageToken==imageGeneration && !isDestroyed() && image.isAttachedToWindow()) {
                        placeholder.setText(video.title()+"\n\n海报加载失败\n"+reason);
                        placeholder.setTextSize(12);
                    }
                });
            }
        }));
    }
    private void chooseSource() {
        String[] names=new String[sources.size()]; for (int i=0;i<names.length;i++) names[i]=sources.get(i).name;
        new AlertDialog.Builder(this).setTitle("选择首页来源").setItems(names,(d,i) -> { selected=sources.get(i); keyword=""; category=""; categoryName="全部分类"; types.clear(); home(); }).setNegativeButton("取消",null).show();
    }
    private void chooseCategory() {
        if (selected==null) { settings(); return; }
        if (!types.isEmpty()) { categoryDialog(); return; }
        toast("正在获取分类…"); Catalog.Source source=selected; int token=generation;
        workers.submit(() -> {
            try { List<String[]> result=Catalog.categories(source); runOnUiThread(() -> { if (token==generation && selected==source && screen.equals("home")) { types.clear(); types.addAll(result); if (types.isEmpty()) toast("该来源没有提供分类"); else categoryDialog(); } }); }
            catch(Exception e) { runOnUiThread(() -> { if (token==generation) toast("分类加载失败，请更换来源"); }); }
        });
    }
    private void categoryDialog() {
        String[] names=new String[types.size()+1]; names[0]="全部分类"; for(int i=0;i<types.size();i++) names[i+1]=types.get(i)[1];
        new AlertDialog.Builder(this).setTitle("浏览分类").setItems(names,(d,i) -> { category=i==0?"":types.get(i-1)[0]; categoryName=names[i]; keyword=""; search.setText(""); categoryButton.setText(categoryName); load(true); }).setNegativeButton("取消",null).show();
    }
    private void openDetail(Catalog.Video video) {
        cancel(); int token=generation; screen="detail"; detail=video; lines=Collections.emptyList(); shell(video.title()); body.addView(text("正在获取影片详情…",22,Color.WHITE));
        Button back=button("返回列表",this::returnHome); body.addView(back); back.requestFocusFromTouch();
        pending.add(workers.submit(() -> {
            try { Catalog.Video full=Catalog.detail(video); runOnUiThread(() -> { if (token==generation && screen.equals("detail")) { detail=full; lines=Catalog.lines(full); currentLine=0; JSONObject p=store.progress(full); for(int i=0;i<lines.size();i++) if(lines.get(i).name.equals(p.optString("line"))) currentLine=i; renderDetail(); } }); }
            catch (Exception e) { runOnUiThread(() -> { if (token==generation) { body.addView(text("详情加载失败："+Network.describe(e),18,Color.LTGRAY)); body.addView(button("重试",() -> openDetail(video))); } }); }
        }));
    }
    private void renderDetail() {
        shell("影片详情"); TextView heading=text(detail.title(),30,TvStyle.TEXT); heading.setTypeface(null,Typeface.BOLD); heading.setSingleLine(); heading.setEllipsize(android.text.TextUtils.TruncateAt.END); body.addView(heading);
        TextView meta=text(detail.meta()+"  ·  "+detail.source.name,15,TvStyle.LAVENDER); meta.setPadding(0,dp(6),0,0); body.addView(meta);
        TextView desc=text(Html.fromHtml(detail.raw.optString("vod_content")).toString().trim(),15,TvStyle.MUTED);
        desc.setMaxLines(3); desc.setPadding(0,dp(12),0,dp(14)); body.addView(desc);
        LinearLayout actions=row(); actions.addView(button("返回列表",this::returnHome));
        actions.addView(button("搜索其他来源",() -> { keyword=detail.title(); category=""; home(); }));
        if (!lines.isEmpty()) {
            JSONObject p=store.progress(detail);
            int resume=Math.min(p.optInt("episode",0),lines.get(currentLine).episodes.size()-1);
            actions.addView(button(p.length()>0 ? "继续观看 · "+lines.get(currentLine).episodes.get(resume).name : "立即播放",() -> play(resume,true)));
            actions.addView(button("线路 · "+lines.get(currentLine).name,() -> {
                String[] names=new String[lines.size()]; for(int i=0;i<names.length;i++) names[i]=lines.get(i).name+" · "+lines.get(i).episodes.size()+" 集";
                new AlertDialog.Builder(this).setTitle("选择播放线路").setItems(names,(d,i) -> { currentLine=i; renderDetail(); }).show();
            }));
        }
        body.addView(actions);
        if (lines.isEmpty()) { body.addView(text("该影片暂无可直接播放的媒体线路，可搜索其他来源。",18,Color.LTGRAY)); actions.getChildAt(0).requestFocusFromTouch(); return; }
        TextView label=text("选集  ·  "+lines.get(currentLine).episodes.size()+" 集",17,TvStyle.TEXT); label.setTypeface(null,Typeface.BOLD); label.setPadding(0,dp(16),0,dp(10)); body.addView(label);
        ScrollView episodes=new ScrollView(this); episodes.setClipToPadding(false); episodes.setPadding(dp(4),dp(4),dp(4),0); TvStyle.viewport(episodes); GridLayout list=new GridLayout(this); list.setColumnCount(6); list.setClipChildren(false);
        for(int i=0;i<lines.get(currentLine).episodes.size();i++) {
            int index=i; Button b=button(lines.get(currentLine).episodes.get(i).name,() -> play(index,false));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=(getResources().getDisplayMetrics().widthPixels-dp(80))/6-dp(8); lp.height=dp(44); lp.setMargins(0,0,dp(8),dp(10)); b.setLayoutParams(lp); list.addView(b); if(i<12) TvStyle.enter(b,(i%6)*25);
        }
        episodes.addView(list); body.addView(episodes,new LinearLayout.LayoutParams(-1,0,1)); actions.getChildAt(2).requestFocusFromTouch();
    }
    private void play(int episode, boolean resume) {
        try {
            // 大型选集资料通过私有文件传递，避免超过 Binder 的 Intent 大小限制。
            try (java.io.FileOutputStream out=openFileOutput("playback.json",MODE_PRIVATE)) { out.write(detail.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            Intent intent=new Intent(this,PlayerActivity.class); intent.putExtra("line",currentLine); intent.putExtra("episode",episode); intent.putExtra("resume",resume); startActivity(intent);
        } catch (Exception e) { toast("无法打开播放器："+e.getMessage()); }
    }
    private void returnHome() {
        if (selected==null || !sources.contains(selected) || !selected.enabled) {
            Catalog.Source replacement=firstEnabled();
            if (selected!=replacement) {
                selected=replacement; category=""; categoryName="全部分类"; types.clear();
            }
        }
        home();
    }
    private void history() {
        cancel(); screen="history"; shell("继续观看"); LinearLayout actions=row(); actions.addView(button("返回首页",this::returnHome));
        actions.addView(button("清空记录",() -> new AlertDialog.Builder(this).setTitle("清空观看记录？").setMessage("将同时清除本机续播进度。").setPositiveButton("清空",(d,w) -> { store.clearHistory(); history(); }).setNegativeButton("取消",null).show())); body.addView(actions);
        ScrollView sc=new ScrollView(this); TvStyle.viewport(sc); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(0,dp(16),0,0);
        JSONArray data=store.history(); if(data.length()==0) list.addView(text("还没有观看记录，选一部影片开始吧。",20,Color.LTGRAY));
        for(int i=0;i<data.length();i++) {
            try { JSONObject p=data.getJSONObject(i); Catalog.Video v=Catalog.Video.from(p.getJSONObject("video"));
                Button b=button(v.title()+"  ·  第 "+(p.optInt("episode")+1)+" 集  ·  "+(p.optLong("position")/60000)+" 分钟  ·  "+v.source.name,() -> openDetail(v));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52)); lp.setMargins(0,0,0,dp(10)); list.addView(b,lp);
            } catch(Exception ignored) { }
        }
        sc.addView(list); body.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); actions.getChildAt(0).requestFocusFromTouch();
    }
    private void settings() {
        cancel(); screen="settings"; shell("影视源管理");
        TextView help=text("管理你的影视来源。启用的来源参与搜索，首页可单独选择浏览来源。",15,TvStyle.MUTED); body.addView(help);
        LinearLayout actions=row(); actions.setPadding(0,dp(14),0,dp(14)); actions.addView(button("返回首页",this::returnHome));
        actions.addView(button("添加来源",this::addSource)); actions.addView(button("关于",() -> new AlertDialog.Builder(this).setTitle("LibreTV 家庭影院 1.1.9").setMessage("基于 LibreSpark/LibreTV 的苹果 CMS 协议，原生 Android TV 实现。\n\n无账号、无启动密码、无自建后端。数据与视频由第三方源直接提供，观看记录仅保存在本机。\n\n上游：https://github.com/LibreSpark/LibreTV\n许可：GNU AGPL-3.0，源码随项目提供。\n播放器：AndroidX Media3（Apache-2.0）。").setPositiveButton("关闭",null).show())); body.addView(actions);
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
    @Override protected void onResume() {
        super.onResume();
        if(screen.equals("detail") && detail!=null && !lines.isEmpty()) {
            JSONObject progress=store.progress(detail);
            for(int i=0;i<lines.size();i++) if(lines.get(i).name.equals(progress.optString("line"))) currentLine=i;
            renderDetail();
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if (key==focusRecoveryKey) {
            if (event.getAction()==KeyEvent.ACTION_UP) focusRecoveryKey=KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        boolean direction=key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN
            || key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT;
        View focused=getCurrentFocus();
        // 触摸或列表更新可能让焦点落到容器，第一下方向键只恢复首页入口。
        if (screen.equals("home") && direction && event.getAction()==KeyEvent.ACTION_DOWN
            && (focused==null || !focused.isEnabled() || !focused.isClickable())) {
            if (historyButton.requestFocusFromTouch()) { focusRecoveryKey=key; return true; }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() {
        if (!screen.equals("home")) { returnHome(); return; }
        if (exitDialog!=null) return;
        exitDialog=new AlertDialog.Builder(this).setTitle("退出软件？")
            .setMessage("确定退出 LibreTV 家庭影院吗？")
            .setPositiveButton("确定",(d,w) -> finishAndRemoveTask())
            .setNegativeButton("取消",null).create();
        exitDialog.setOnDismissListener(d -> exitDialog=null);
        exitDialog.show();
        exitDialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocusFromTouch();
    }
    @Override protected void onStop() { focusRecoveryKey=KeyEvent.KEYCODE_UNKNOWN; super.onStop(); }
    @Override protected void onDestroy() { cancel(); workers.shutdownNow(); images.shutdownNow(); covers.evictAll(); super.onDestroy(); }
}

