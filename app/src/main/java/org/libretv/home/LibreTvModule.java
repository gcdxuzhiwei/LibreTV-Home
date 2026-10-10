package org.libretv.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.facebook.react.bridge.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/** 复用设备端 CMS 与私有存储，JS 只负责页面和焦点交互。 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class LibreTvModule extends ReactContextBaseJavaModule {
    private final ThreadPoolExecutor workers = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
    // 图片补全单独限流，不能占满列表、详情和播放使用的任务线程。
    private final ThreadPoolExecutor coverWorkers = new ThreadPoolExecutor(2, 2, 0,
            java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(32));
    private final List<PendingRequest> pending = new ArrayList<>();
    private final LocalStore store;
    LibreTvModule(ReactApplicationContext context) { super(context); store = new LocalStore(context); }
    @Override public String getName() { return "LibreTV"; }
    private interface Request { String run() throws Exception; }
    private void request(Promise promise, Request task) {
        // 本地记录读取不排在网络任务后面。
        try { promise.resolve(task.run()); }
        catch (Exception error) { promise.reject("LIBRETV_ERROR", Network.describe(error), error); }
    }
    private final class PendingRequest {
        final String group;
        final Promise promise;
        final Network.RequestScope scope = new Network.RequestScope();
        final AtomicBoolean settled = new AtomicBoolean();
        final FutureTask<Void> future;
        PendingRequest(String group, Promise promise, Request task) {
            this.group = group; this.promise = promise;
            future = new FutureTask<>(() -> {
                Network.enter(scope);
                try {
                    String result = task.run();
                    if (settled.compareAndSet(false, true)) promise.resolve(result);
                } catch (Exception error) {
                    if (settled.compareAndSet(false, true)) promise.reject("LIBRETV_ERROR", Network.describe(error), error);
                } finally {
                    Network.leave();
                    synchronized (pending) { pending.remove(this); }
                }
                return null;
            });
        }
        void cancel() {
            scope.cancel(); future.cancel(true);
            if (settled.compareAndSet(false, true)) promise.reject("REQUEST_CANCELLED", "请求已取消");
        }
    }
    private void request(String group, Promise promise, Request task) {
        synchronized (pending) {
            PendingRequest item = new PendingRequest(group, promise, task);
            pending.add(item); workers.execute(item.future);
        }
    }
    @ReactMethod public void cancelRequests(String group) {
        synchronized (pending) {
            for (PendingRequest item : new ArrayList<>(pending)) {
                if (item.group.equals(group)) { item.cancel(); pending.remove(item); }
            }
            workers.purge();
            coverWorkers.purge();
        }
    }
    private Catalog.Source source(String json) throws Exception {
        JSONObject row = new JSONObject(json);
        return new Catalog.Source(row.getString("name"), row.getString("url"), row.optBoolean("enabled", true));
    }
    @ReactMethod public void sources(Promise promise) {
        request(promise, () -> {
            JSONArray rows = new JSONArray();
            for (Catalog.Source item : store.sources()) rows.put(item.json());
            return rows.toString();
        });
    }
    @ReactMethod public void coverCandidates(String json, String requestId, Promise promise) {
        synchronized (pending) {
            PendingRequest item = new PendingRequest("cover:" + requestId, promise, () ->
                    CoverFallback.candidates(Catalog.Video.from(new JSONObject(json)), store.sources()));
            pending.add(item);
            try {
                coverWorkers.execute(item.future);
            } catch (java.util.concurrent.RejectedExecutionException error) {
                pending.remove(item);
                if (item.settled.compareAndSet(false, true))
                    promise.reject("COVER_BUSY", "封面补全队列繁忙，请稍后重试", error);
            }
        }
    }
    @ReactMethod public void page(String json, String keyword, String category, int number, Promise promise) {
        request("page", promise, () -> {
            Catalog.Page page = Catalog.page(source(json), keyword, category, Math.max(1, number));
            JSONArray videos = new JSONArray();
            for (Catalog.Video video : page.videos) videos.put(video.json());
            return new JSONObject().put("videos", videos).put("pages", page.pages).toString();
        });
    }
    @ReactMethod public void categories(String json, Promise promise) {
        request("categories", promise, () -> {
            JSONArray rows = new JSONArray();
            for (String[] row : Catalog.categories(source(json))) rows.put(new JSONObject().put("id", row[0]).put("name", row[1]));
            return rows.toString();
        });
    }
    @ReactMethod public void detail(String json, Promise promise) {
        request("detail", promise, () -> {
            Catalog.Video video = Catalog.detail(Catalog.Video.from(new JSONObject(json)));
            JSONArray lines = new JSONArray();
            for (Catalog.Line line : Catalog.lines(video)) {
                JSONArray episodes = new JSONArray();
                for (Catalog.Episode episode : line.episodes) episodes.put(episode.name);
                lines.put(new JSONObject().put("name", line.name).put("episodes", episodes));
            }
            return new JSONObject().put("video", video.json()).put("lines", lines).put("progress", store.progress(video)).toString();
        });
    }
    @ReactMethod public void history(Promise promise) { request(promise, () -> store.history().toString()); }
    @ReactMethod public void clearHistory(Promise promise) {
        store.clearHistory(); promise.resolve(null);
    }
    @ReactMethod public void play(String json, int line, int episode, boolean resume, Promise promise) {
        // 文件写入和 Activity 启动在同一 UI 任务中串行执行，防止两次播放互相覆盖。
        getReactApplicationContext().runOnUiQueueThread(() -> {
            try {
                Activity activity = getCurrentActivity();
                if (activity == null || activity.isFinishing()) throw new Exception("页面已关闭");
                Catalog.Video video = Catalog.Video.from(new JSONObject(json));
                List<Catalog.Line> lines = Catalog.lines(video);
                if (line < 0 || line >= lines.size() || episode < 0 || episode >= lines.get(line).episodes.size()) throw new Exception("选集不存在");
                try (FileOutputStream out = activity.openFileOutput("playback.json", Context.MODE_PRIVATE)) {
                    out.write(video.json().toString().getBytes(StandardCharsets.UTF_8));
                }
                activity.startActivity(new Intent(activity, PlayerActivity.class).putExtra("line", line).putExtra("episode", episode).putExtra("resume", resume));
                promise.resolve(null);
            } catch (Exception error) { promise.reject("PLAY_ERROR", Network.describe(error), error); }
        });
    }
    @ReactMethod public void openSettings() {
        getReactApplicationContext().runOnUiQueueThread(() -> {
            Activity activity = getCurrentActivity();
            if (activity != null) activity.startActivity(new Intent(activity, SettingsActivity.class));
        });
    }
    @Override public void invalidate() {
        coverWorkers.shutdownNow();
        synchronized (pending) {
            for (PendingRequest item : pending) item.cancel();
            pending.clear(); workers.shutdownNow();
        }
        super.invalidate();
    }
}
