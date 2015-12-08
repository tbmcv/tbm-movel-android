package net.tbmcv.tbmmovel;

import android.content.Context;

import org.linphone.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public abstract class TbmApi {
    static TbmApi instance;

    public static synchronized TbmApi getInstance(Context context) {
        if (instance == null) {
            try {
                instance = new TbmApi.Default(context);
            } catch (IOException | GeneralSecurityException e) {
                throw new Error(e);  // TODO
            }
        }
        return instance;
    }

    public abstract URL getBaseUrl();

    public abstract JsonRestClient getRestClient();

    public static class Default extends TbmApi {
        private final SSLContext sslContext;
        private final URL baseUrl;

        private Default(Context context) throws IOException, GeneralSecurityException {
            sslContext = SSLContext.getInstance("TLS");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            InputStream certStream = context.getResources().openRawResource(R.raw.tbmds_cert);
            try {
                keyStore.setCertificateEntry("tbmds",
                        CertificateFactory.getInstance("X.509").generateCertificate(certStream));
            } finally {
                certStream.close();
            }
            trustManagerFactory.init(keyStore);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            baseUrl = new URL(context.getString(R.string.tbm_api_base_url));
        }

        @Override
        public JsonRestClient getRestClient() {
            return new HttpsJsonRestClient(getBaseUrl(), sslContext);
        }

        @Override
        public URL getBaseUrl() {
            return baseUrl;
        }
    }
}
