/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.shared;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Makes HTTPS work on outdated Java 8 installs WITHOUT weakening certificate checks.
 *
 * <p>Old JREs lack newer root CAs (DigiCert Global Root G2 used by Mojang/Microsoft, ISRG Root X1, GTS roots used by
 * Cloudflare, ...), which made connections fail with "PKIX path building failed". This installs, as the JVM-wide
 * default for {@link HttpsURLConnection}, a trust manager that accepts a server chain only if it validates against
 * the JVM's own trust store OR against the bundled roots in {@code tls/extra-roots.pem}. Every chain is still fully
 * validated and the default hostname verification stays in place.</p>
 *
 * <p>If anything goes wrong while setting this up, the JVM defaults are left untouched (strict, just without the
 * extra roots).</p>
 */
public final class TrustedTls {

    private static final Logger log = Logger.getLogger(TrustedTls.class.getName());
    private static boolean installed;

    private TrustedTls() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            X509TrustManager system = trustManagerFor(null);
            X509TrustManager bundled = trustManagerFor(loadBundledRoots());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new EitherTrustManager(system, bundled)}, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not add bundled root certificates, using the JVM trust store only", e);
        }
    }

    private static KeyStore loadBundledRoots() throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream in = TrustedTls.class.getResourceAsStream("tls/extra-roots.pem")) {
            if (in == null) {
                throw new IllegalStateException("tls/extra-roots.pem is missing");
            }
            int i = 0;
            for (Certificate certificate : factory.generateCertificates(in)) {
                store.setCertificateEntry("gummercraft-extra-" + i++, certificate);
            }
        }
        return store;
    }

    /** @param store trust anchors, or {@code null} for the JVM default trust store */
    private static X509TrustManager trustManagerFor(KeyStore store) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("No X509TrustManager available");
    }

    /** Trusts a server chain if either delegate validates it; client certificates use the JVM defaults only. */
    private static final class EitherTrustManager implements X509TrustManager {
        private final X509TrustManager primary;
        private final X509TrustManager fallback;

        EitherTrustManager(X509TrustManager primary, X509TrustManager fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                primary.checkServerTrusted(chain, authType);
            } catch (CertificateException | RuntimeException e) {
                // RuntimeException: e.g. a broken/empty JVM trust store ("trustAnchors parameter must be non-empty")
                try {
                    fallback.checkServerTrusted(chain, authType);
                } catch (CertificateException ignored) {
                    if (e instanceof CertificateException) {
                        throw (CertificateException) e;
                    }
                    throw new CertificateException(e);
                }
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            primary.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = primary.getAcceptedIssuers();
            X509Certificate[] b = fallback.getAcceptedIssuers();
            X509Certificate[] all = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }
    }
}
