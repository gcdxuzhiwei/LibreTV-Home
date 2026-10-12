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

/** 提供可取消的设备网络请求、私有存储与原生播放；CMS 业务在 TS 中处理。 */
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
    @Override public java.util.Map<String, Object> getConstants() {
        return java.util.Collections.singletonMap("version", BuildConfig.VERSION_NAME);
    }
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
    @ReactMethod public void saveSources(String json, Promise promise) {
        request(promise, () -> {
            JSONArray rows = new JSONArray(json);
            List<Catalog.Source> sources = new ArrayList<>();
            java.util.Set<String> urls = new java.util.HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                Catalog.Source row = source(rows.getJSONObject(i).toString());
                Catalog.Source item = new Catalog.Source(row.name.trim(), row.url.trim(), row.enabled);
                if (item.name.isEmpty() || !Catalog.http(item.url)) throw new Exception("填写名称和有效的 http(s) 接口地址");
                if (!urls.add(item.url)) throw new Exception("该地址已存在");
                sources.add(item);
            }
            store.saveSources(sources);
            return null;
        });
    }
    @ReactMethod public void fetchCms(String address, String group, Promise promise) {
        Request task = () -> new String(Catalog.get(address, 8 * 1024 * 1024), StandardCharsets.UTF_8);
        if (!group.startsWith("cover:")) { request(group, promise, task); return; }
        synchronized (pending) {
            PendingRequest item = new PendingRequest(group, promise, task);
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
    @ReactMethod public void history(Promise promise) { request(promise, () -> store.history().toString()); }
    @ReactMethod public void clearHistory(Promise promise) {
        store.clearHistory(); promise.resolve(null);
    }
    // 订阅后读取快照，避免 React 根视图挂载前的播放器错误事件丢失。
    @ReactMethod public void playerDialog(Promise promise) {
        getReactApplicationContext().runOnUiQueueThread(() -> {
            Activity activity=getCurrentActivity();
            promise.resolve(activity instanceof PlayerActivity ? ((PlayerActivity)activity).playerDialog() : null);
        });
    }
    @ReactMethod public void playerDialogAction(int id,String action,int index) {
        getReactApplicationContext().runOnUiQueueThread(() -> {
            Activity activity=getCurrentActivity();
            if(activity instanceof PlayerActivity) ((PlayerActivity)activity).playerDialogAction(id,action,index);
        });
    }
    @ReactMethod public void play(String json, int line, int episode, boolean resume, Promise promise) {
        // 文件写入和 Activity 启动在同一 UI 任务中串行执行，防止两次播放互相覆盖。
        getReactApplicationContext().runOnUiQueueThread(() -> {
            try {
                Activity activity = getCurrentActivity();
                if (activity == null || activity.isFinishing()) throw new Exception("页面已关闭");
                JSONObject payload = new JSONObject(json);
                List<Catalog.Line> lines = Catalog.playbackLines(payload.getJSONArray("playbackLines"));
                if (line < 0 || line >= lines.size() || episode < 0 || episode >= lines.get(line).episodes.size()) throw new Exception("选集不存在");
                try (FileOutputStream out = activity.openFileOutput("playback.json", Context.MODE_PRIVATE)) {
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                activity.startActivity(new Intent(activity, PlayerActivity.class).putExtra("line", line).putExtra("episode", episode).putExtra("resume", resume));
                promise.resolve(null);
            } catch (Exception error) { promise.reject("PLAY_ERROR", Network.describe(error), error); }
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
