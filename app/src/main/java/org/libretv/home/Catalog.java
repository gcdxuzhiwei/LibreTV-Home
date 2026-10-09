package org.libretv.home;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** 将 LibreTV 的苹果 CMS 协议移至设备端，不经过任何应用服务器。 */
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
        public String meta() { return raw.optString("vod_year") + "  " + raw.optString("type_name") + "  " + raw.optString("vod_remarks"); }
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
    public static final class Page {
        public final List<Video> videos = new ArrayList<>();
        public final List<String[]> categories = new ArrayList<>();
        public int pages = 1;
    }
    public static boolean http(String url) {
        try { URI u = new URI(url); return ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && u.getHost() != null; }
        catch (Exception e) { return false; }
    }
    public static byte[] get(String address, int limit) throws Exception {
        if (!http(address)) throw new Exception("地址需要以 http:// 或 https:// 开头");
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "*/*");
        try {
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (out.size() + n > limit) throw new Exception("响应过大");
                    out.write(b, 0, n);
                }
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }
    private static JSONObject request(Source s, String... params) throws Exception {
        String base = s.url.split("#", 2)[0]; StringBuilder address = new StringBuilder(base);
        for (int i = 0; i < params.length; i += 2) {
            address.append(address.indexOf("?") < 0 ? '?' : '&').append(URLEncoder.encode(params[i], "UTF-8"))
                .append('=').append(URLEncoder.encode(params[i + 1], "UTF-8"));
        }
        String body = new String(get(address.toString(), 8 * 1024 * 1024), StandardCharsets.UTF_8);
        if (body.startsWith("\uFEFF")) body = body.substring(1);
        try { return new JSONObject(body); }
        catch (Exception e) { throw new Exception("接口未返回苹果 CMS JSON 数据"); }
    }
    public static Page page(Source source, String keyword, String category, int page) throws Exception {
        List<String> params = new ArrayList<>();
        params.add("ac"); params.add("videolist"); params.add("pg"); params.add(String.valueOf(page));
        if (!keyword.isEmpty()) { params.add("wd"); params.add(keyword); }
        if (!category.isEmpty()) { params.add("t"); params.add(category); }
        JSONObject data = request(source, params.toArray(new String[0]));
        Page result = new Page(); result.pages = Math.max(1, data.optInt("pagecount", 1));
        JSONArray list = data.optJSONArray("list");
        if (list == null && data.has("list") && !data.isNull("list")) throw new Exception("列表格式无效");
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject v = list.optJSONObject(i);
            if (v != null && !v.optString("vod_id").isEmpty() && !adult(v.optString("type_name"))) result.videos.add(new Video(source, v));
        }
        result.categories.addAll(parseCategories(data.optJSONArray("class")));
        return result;
    }
    public static List<String[]> categories(Source s) throws Exception {
        return parseCategories(request(s, "ac", "list").optJSONArray("class"));
    }
    private static List<String[]> parseCategories(JSONArray list) throws Exception {
        List<String[]> out = new ArrayList<>(); Set<String> parents = new HashSet<>();
        // 部分 CMS 的父分类没有影片，使用叶子分类才能可靠浏览。
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject t = list.getJSONObject(i); String parent = t.optString("type_pid");
            if (!parent.isEmpty() && !parent.equals("0")) parents.add(parent);
        }
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject t = list.getJSONObject(i);
            if (!adult(t.optString("type_name")) && !parents.contains(t.optString("type_id"))) out.add(new String[]{t.optString("type_id"), t.optString("type_name")});
        }
        return out;
    }
    private static boolean adult(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        for (String word : new String[]{"伦理", "倫理", "福利", "色情", "无码", "有码", "里番", "传媒", "写真", "萝莉", "诱惑", "擦边", "成人视频", "cosplay", "swag"}) if (t.contains(word)) return true;
        return false;
    }
    public static Video detail(Video video) throws Exception {
        JSONObject data = request(video.source, "ac", "videolist", "ids", video.id());
        JSONArray list = data.optJSONArray("list");
        if (list == null || list.length() == 0) throw new Exception("影片详情为空");
        return new Video(video.source, list.getJSONObject(0));
    }
    public static List<Line> lines(Video video) {
        List<Line> lines = new ArrayList<>();
        String[] groups = video.raw.optString("vod_play_url").split("\\$\\$\\$");
        String[] names = video.raw.optString("vod_play_from").split("\\$\\$\\$");
        for (int i = 0; i < groups.length; i++) {
            Line line = new Line(i < names.length ? names[i] : "线路 " + (i + 1));
            for (String entry : groups[i].split("#")) {
                int split = entry.indexOf('$'); if (split < 0) continue;
                String url = entry.substring(split + 1).trim();
                String path = null;
                try { path = new URI(url).getPath(); } catch (Exception ignored) { }
                // 网页解析线路不能交给原生播放器，保留直接媒体地址。
                if (http(url) && path != null && path.toLowerCase(Locale.ROOT).matches(".*\\.(m3u8|mp4|m4v|mkv|webm|mpd|ts)$")) {
                    line.episodes.add(new Episode(entry.substring(0, split), url));
                }
            }
            if (!line.episodes.isEmpty()) lines.add(line);
        }
        return lines;
    }
    private Catalog() {}
}
