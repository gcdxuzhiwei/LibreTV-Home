package org.libretv.home;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Call;
import okhttp3.Request;
import java.util.ArrayList;
import java.util.List;

/** 接口、海报和媒体共享网络配置，保留系统证书与域名校验。 */
public final class Network {
    // 只取消当前桥接任务的 CMS 请求，不影响海报和正在播放的媒体。
    private static final ThreadLocal<RequestScope> REQUEST_SCOPE = new ThreadLocal<>();
    static final class RequestScope {
        private final List<Call> calls = new ArrayList<>();
        private boolean cancelled;
        synchronized void add(Call call) {
            if (cancelled) call.cancel();
            else calls.add(call);
        }
        synchronized void cancel() {
            cancelled = true;
            for (Call call : calls) call.cancel();
            calls.clear();
        }
    }
    static void enter(RequestScope scope) { REQUEST_SCOPE.set(scope); }
    static void leave() { REQUEST_SCOPE.remove(); }
    static Call newCall(Request request) {
        Call call = CLIENT.newCall(request);
        RequestScope scope = REQUEST_SCOPE.get();
        if (scope != null) scope.add(call);
        return call;
    }
    public static volatile OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();

    /** 在 Application 启动时执行，先于 CMS、Media3 和 React Native 图片请求。 */
    static void initialize(android.content.Context context) {
        try (java.io.InputStream root = context.getResources().openRawResource(R.raw.isrg_root_x1)) {
            X509TrustManager trust = CompatibleTrust.create(root);
            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(null, new TrustManager[]{trust}, null);
            CLIENT = CLIENT.newBuilder().sslSocketFactory(tls.getSocketFactory(), trust).build();
        } catch (java.io.IOException | java.security.GeneralSecurityException error) {
            // 根证书加载失败仍使用系统校验，不能退化为信任所有证书。
            android.util.Log.e("LibreTVNetwork", "加载兼容根证书失败", error);
        }
    }

    public static String describe(Throwable error) {
        String reason = null;
        Throwable deepest = error;
        // 保留异常链中的具体原因，证书错误优先于外层 TLS 错误。
        for (int i = 0; deepest != null && i < 16; i++) {
            if (deepest instanceof CertificateException || deepest instanceof SSLPeerUnverifiedException) {
                reason = "HTTPS 证书校验失败，请检查设备日期、系统证书和来源证书";
                break;
            }
            if (deepest instanceof SSLException) reason = "HTTPS/TLS 握手或安全连接失败";
            else if (deepest instanceof UnknownHostException) reason = "DNS 无法解析服务器域名";
            else if (deepest instanceof SocketTimeoutException) reason = "服务器连接或读取超时";
            else if (deepest instanceof ConnectException) reason = "无法连接服务器";
            if (deepest.getCause() == null || deepest.getCause() == deepest) break;
            deepest = deepest.getCause();
        }
        String detail = deepest == null ? "未知错误" : deepest.getClass().getSimpleName();
        String message = deepest == null ? null : deepest.getMessage();
        // 错误提示只展示有限长度，并移除链接中的查询参数，避免泄露播放令牌。
        if (message != null && !message.isEmpty()) {
            message = message.replaceAll("(https?://[^\\s?]+)\\?[^\\s]+", "$1?…");
            detail += ": " + message.substring(0, Math.min(240, message.length()));
        }
        return reason == null ? detail : reason + "\n" + detail;
    }

    private Network() {}
}
