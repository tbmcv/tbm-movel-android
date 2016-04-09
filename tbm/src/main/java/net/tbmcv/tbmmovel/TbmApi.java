package net.tbmcv.tbmmovel;
/*
TbmApi.java
Copyright (C) 2016  TBM Comunicações, Lda.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
import android.content.Context;

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
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
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
            return new HttpsJsonRestClient(getBaseUrl(), sslContext) {
                @Override
                public JsonRestClient.RequestBuilder buildRequest() {
                    return super.buildRequest()
                            .connectTimeout(4000)
                            .readTimeout(8000);
                }
            };
        }

        @Override
        public URL getBaseUrl() {
            return baseUrl;
        }
    }
}
