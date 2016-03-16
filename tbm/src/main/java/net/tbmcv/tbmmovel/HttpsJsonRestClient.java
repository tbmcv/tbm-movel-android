package net.tbmcv.tbmmovel;
/*
HttpsJsonRestClient.java
Copyright (C) 2016  Daniel Getz

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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class HttpsJsonRestClient extends HttpJsonRestClient {
    private final SSLContext sslContext;

    public HttpsJsonRestClient(URL baseUrl, SSLContext sslContext) {
        super(baseUrl);
        this.sslContext = sslContext;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    @Override
    protected HttpsURLConnection openConnection(URI uri) throws IOException {
        HttpURLConnection urlConnection = super.openConnection(uri);
        HttpsURLConnection connection;
        try {
            connection = (HttpsURLConnection) urlConnection;
        } catch (ClassCastException e) {
            throw new IOException("Not an HTTPS URL");
        }
        connection.setSSLSocketFactory(getSslContext().getSocketFactory());
        return connection;
    }
}
