package org.libretv.home;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** 原生存储与播放器的数据载体，以及复用兼容网络栈的受限读取。 */
public final class Catalog {
    public static final String UA = "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36";
    public static final class Source {
        public final String name, url;
        public boolean enabled;
        public Source(String name, String url, boolean enabled) { this.name = name; this.url = url; this.enabled = enabled; }
        public JSONObject json() throws Exception { return new JSONObject().put("name", name).put("url", url).put("enabled", enabled); }
    }
    public static final class Video {
        public final Source source;
        public final JSONObject raw;
        public Video(Source source, JSONObject raw) { this.source = source; this.raw = raw; }
        public String id() { return raw.optString("vod_id"); }
        public String title() { return raw.optString("vod_name", "未命名影片"); }
        public String key() { return source.url + "|" + id(); }
        public JSONObject json() throws Exception { return new JSONObject().put("source", source.json()).put("vod", raw); }
        public static Video from(JSONObject obj) throws Exception {
            JSONObject s = obj.getJSONObject("source");
            return new Video(new Source(s.getString("name"), s.getString("url"), true), obj.getJSONObject("vod"));
        }
    }
    public static final class Episode {
        public final String name, url;
        public Episode(String name, String url) { this.name = name; this.url = url; }
    }
    public static final class Line {
        public final String name;
        public final List<Episode> episodes = new ArrayList<>();
        public Line(String name) { this.name = name; }
    }
    public static boolean http(String url) {
        try { URI u = new URI(url); return ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && u.getHost() != null; }
        catch (Exception e) { return false; }
    }
    public static byte[] get(String address, int limit) throws Exception {
        if (!http(address)) throw new Exception("地址需要以 http:// 或 https:// 开头");
        Request request = new Request.Builder().url(address).header("User-Agent", UA).header("Accept", "*/*").build();
        try (Response response = Network.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new Exception("服务器返回空响应");
            if (body.contentLength() > limit) throw new Exception("响应过大");
            try (InputStream in = body.byteStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (out.size() + n > limit) throw new Exception("响应过大");
                    out.write(b, 0, n);
                }
                return out.toByteArray();
            }
        }
    }
    /** 只读取 TS 已解析的播放资料，不再重复解析 CMS 协议。 */
    public static List<Line> playbackLines(JSONArray data) throws Exception {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.getJSONObject(i);
            Line line = new Line(row.getString("name"));
            JSONArray episodes = row.getJSONArray("episodes");
            for (int j = 0; j < episodes.length(); j++) {
                JSONObject episode = episodes.getJSONObject(j);
                String url = episode.getString("url");
                if (!http(url)) throw new Exception("播放地址无效");
                line.episodes.add(new Episode(episode.getString("name"), url));
            }
            if (line.episodes.isEmpty()) throw new Exception("播放线路为空");
            lines.add(line);
        }
        return lines;
    }
    private Catalog() {}
}
