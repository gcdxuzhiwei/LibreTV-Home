package org.libretv.home;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;

/** 图片失败后只从已启用的 CMS 查找同一影片，不改变影片或播放来源。 */
final class CoverFallback {
    static boolean sameFilm(Catalog.Video original, Catalog.Video candidate) {
        String name = normalize(original.raw.optString("vod_name"));
        String year = original.raw.optString("vod_year").trim();
        String type = normalize(original.raw.optString("type_name"));
        // 缺少辨识资料时保留占位图，避免将同名的不同影片拼在一起。
        return !name.isEmpty() && year.matches("\\d{4}") && !type.isEmpty()
                && name.equals(normalize(candidate.raw.optString("vod_name")))
                && year.equals(candidate.raw.optString("vod_year").trim())
                && type.equals(normalize(candidate.raw.optString("type_name")));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
    }

    static String candidates(Catalog.Video original, List<Catalog.Source> sources) throws Exception {
        Set<String> urls = new LinkedHashSet<>();
        String failedUrl = original.raw.optString("vod_pic").trim();
        Exception failure = null;
        int searched = 0;
        for (Catalog.Source source : sources) {
            if (!source.enabled || source.url.equals(original.source.url)) continue;
            if (++searched > 4) break;
            try {
                for (Catalog.Video candidate : Catalog.page(source, original.title(), "", 1).videos) {
                    String url = candidate.raw.optString("vod_pic").trim();
                    if (sameFilm(original, candidate) && Catalog.http(url) && !url.equals(failedUrl)) urls.add(url);
                }
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) throw error;
                failure = error;
                // 某个候选来源不可用仍可继续尝试其他已启用来源。
            }
        }
        // 部分来源查找失败时，空结果不能作为确定的“无匹配”缓存。
        if (urls.isEmpty() && failure != null) throw failure;
        return new JSONArray(urls).toString();
    }

    private CoverFallback() {}
}
