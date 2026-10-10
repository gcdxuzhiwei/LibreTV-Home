package org.libretv.home;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** 系统信任库加官方 ISRG Root X1，兼容缺少此根证书的 Android 6 电视。 */
final class CompatibleTrust implements X509TrustManager {
    private final X509TrustManager system, additional;

    private CompatibleTrust(X509TrustManager system, X509TrustManager additional) {
        this.system = system;
        this.additional = additional;
    }

    static X509TrustManager create(InputStream root) throws GeneralSecurityException, java.io.IOException {
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(root);
        certificate.checkValidity();
        if (certificate.getBasicConstraints() < 0) throw new CertificateException("兼容证书必须是 CA 根证书");
        certificate.verify(certificate.getPublicKey());
        KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
        extra.load(null, null);
        extra.setCertificateEntry("isrg-root-x1", certificate);
        return new CompatibleTrust(manager(null), manager(extra));
    }

    private static X509TrustManager manager(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new GeneralSecurityException("没有可用的 X509TrustManager");
    }

    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            system.checkServerTrusted(chain, authType);
        } catch (CertificateException original) {
            try {
                additional.checkServerTrusted(chain, authType);
            } catch (CertificateException rejected) {
                original.addSuppressed(rejected);
                throw original;
            }
        }
    }

    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        system.checkClientTrusted(chain, authType);
    }

    @Override public X509Certificate[] getAcceptedIssuers() {
        X509Certificate[] defaults = system.getAcceptedIssuers(), extras = additional.getAcceptedIssuers();
        X509Certificate[] all = Arrays.copyOf(defaults, defaults.length + extras.length);
        System.arraycopy(extras, 0, all, defaults.length, extras.length);
        return all;
    }
}
