package org.libretv.home;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** 设备本地保存源和观看进度，不需要账号。 */
public final class LocalStore {
    private final SharedPreferences prefs;
    public LocalStore(Context context) { prefs = context.getSharedPreferences("libretv-home", Context.MODE_PRIVATE); }
    public List<Catalog.Source> sources() {
        List<Catalog.Source> out = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(prefs.getString("sources", "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject s = data.getJSONObject(i);
                out.add(new Catalog.Source(s.getString("name"), s.getString("url"), s.optBoolean("enabled", true)));
            }
        } catch (Exception ignored) { }
        if (!prefs.contains("sources")) {
            out.add(new Catalog.Source("非凡资源", "https://api.ffzyapi.com/api.php/provide/vod/", true));
            out.add(new Catalog.Source("量子资源", "https://cj.lziapi.com/api.php/provide/vod/", true));
            out.add(new Catalog.Source("暴风资源", "https://bfzyapi.com/api.php/provide/vod/", true));
        }
        return out;
    }
    public void saveSources(List<Catalog.Source> sources) {
        try { JSONArray data = new JSONArray(); for (Catalog.Source source : sources) data.put(source.json()); prefs.edit().putString("sources", data.toString()).apply(); }
        catch (Exception ignored) { }
    }
    public JSONArray history() {
        try { return new JSONArray(prefs.getString("history", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    public JSONObject progress(Catalog.Video video) {
        JSONArray list = history();
        for (int i = 0; i < list.length(); i++) { JSONObject row = list.optJSONObject(i); if (row != null && video.key().equals(row.optString("key"))) return row; }
        return new JSONObject();
    }
    public void saveProgress(Catalog.Video video, String lineName, int episode, long position, long duration) {
        try {
            JSONObject row = new JSONObject().put("key", video.key()).put("video", video.json())
                .put("line", lineName).put("episode", episode).put("position", Math.max(0, position))
                .put("duration", duration).put("time", System.currentTimeMillis());
            JSONArray old = history(), next = new JSONArray().put(row);
            for (int i = 0; i < old.length() && next.length() < 30; i++) {
                JSONObject item = old.getJSONObject(i);
                if (!video.key().equals(item.optString("key"))) next.put(item);
            }
            prefs.edit().putString("history", next.toString()).apply();
        } catch (Exception ignored) { }
    }
    public void clearHistory() { prefs.edit().remove("history").apply(); }
}
