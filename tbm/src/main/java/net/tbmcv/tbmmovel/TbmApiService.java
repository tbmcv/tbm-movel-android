package net.tbmcv.tbmmovel;
/*
TbmApiService.java
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
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class TbmApiService extends Service {
    static final String LOG_TAG = "TbmApiService";

    private SSLContext sslContext;
    private URL baseUrl;

    public static class Binder extends LocalServiceBinder<TbmApiService> {
        public Binder(TbmApiService service) {
            super(service);
        }
    }

    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate() begins");
        try {
            sslContext = SSLContext.getInstance("TLS");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            InputStream certStream = getResources().openRawResource(R.raw.tbmds_cert);
            try {
                keyStore.setCertificateEntry("tbmds",
                        CertificateFactory.getInstance("X.509").generateCertificate(certStream));
            } finally {
                certStream.close();
            }
            trustManagerFactory.init(keyStore);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            baseUrl = new URL(getString(R.string.tbm_api_base_url));
        } catch (Exception e) {
            throw new AssertionError(e);  // TODO
        }
        Log.d(LOG_TAG, "onCreate() finished");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder(this);
    }

    public RestRequest createRequest() {
        RestRequest request = new RestRequest();
        request.setSslContext(sslContext);
        request.setBaseUrl(baseUrl);
        request.setConnectTimeout(4000);
        request.setReadTimeout(8000);
        return request;
    }

    public URL getBaseUrl() {
        return baseUrl;
    }
}
