package com.blueheronsresistance.stattracker;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Returns custom SSLSocketFactory with pinned cert as provided by R.raw.server.bks
 */
public class pinnedSSLSocketFactory {
    private static char[] KEYSTORE_PASSWORD = "6VPD8pSfr6a79CCvjYJxmFpC".toCharArray();
    public static SSLSocketFactory getSocketFactory(Context ctx) {
        try {
            // Get an instance of the Bouncy Castle KeyStore format
            KeyStore trusted = KeyStore.getInstance("BKS");
            // Get the raw resource, which contains the keystore with
            // your trusted certificates (root and any intermediate certs)
            InputStream in = ctx.getApplicationContext().getResources().openRawResource(R.raw.server);
            //noinspection TryFinallyCanBeTryWithResources
            try {
                // Initialize the keystore with the provided trusted certificates
                // Provide the password of the keystore
                trusted.load(in, KEYSTORE_PASSWORD);
            } finally {
                in.close();
            }

            /*String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf;

            tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(trusted);
            TrustManager[] tms = tmf.getTrustManagers();*/

            TrustManager[] tms = new TrustManager[] {new MyTrustManager(trusted)};


            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tms, null);

            return context.getSocketFactory();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static class MyTrustManager implements X509TrustManager {
        private static final String TAG = "MyVolleyTrustManager";
        private X509TrustManager defaultTrustManager;
        private X509TrustManager localTrustManager;
        private X509Certificate[] acceptedIssuers;

        public MyTrustManager(KeyStore localKeyStore) {
            try {
                String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                TrustManagerFactory tmf;

                tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                tmf.init((KeyStore) null);
                defaultTrustManager = findX509TrustManager(tmf);
                if (defaultTrustManager == null) {
                    throw new IllegalStateException("Couldn't find default X509TrustManager");
                }

                tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                tmf.init(localKeyStore);
                localTrustManager = findX509TrustManager(tmf);
                if (defaultTrustManager == null) {
                    throw new IllegalStateException("Couldn't find local X509TrustManager");
                }

                List<X509Certificate> allIssuers = new ArrayList<X509Certificate>();
                Collections.addAll(allIssuers, defaultTrustManager.getAcceptedIssuers());
                Collections.addAll(allIssuers, localTrustManager.getAcceptedIssuers());
                acceptedIssuers = allIssuers.toArray(new X509Certificate[allIssuers.size()]);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        private X509TrustManager findX509TrustManager(TrustManagerFactory tmf) {
            TrustManager tms[] = tmf.getTrustManagers();
            for (TrustManager tm : tms) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
            return null;
        }
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            try {
                Log.d(TAG, "checkClientTrusted() with default trust manager...");
                defaultTrustManager.checkClientTrusted(chain, authType);
            } catch (CertificateException ce) {
                Log.d(TAG, "checkClientTrusted() with local trust manager...");
                localTrustManager.checkClientTrusted(chain, authType);
            }
        }
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            try {
                Log.d(TAG, "checkServerTrusted() with default trust manager...");
                defaultTrustManager.checkServerTrusted(chain, authType);
            } catch (CertificateException ce) {
                Log.d(TAG, "checkServerTrusted() with local trust manager...");
                localTrustManager.checkServerTrusted(chain, authType);
            }
        }
        public X509Certificate[] getAcceptedIssuers() {
            return acceptedIssuers;
        }
    }
}
