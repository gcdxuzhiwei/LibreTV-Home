package org.libretv.home;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import okhttp3.OkHttpClient;

/** 接口、海报和媒体共享网络配置，保留系统证书与域名校验。 */
public final class Network {
    public static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();

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
