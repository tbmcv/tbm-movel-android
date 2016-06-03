package net.tbmcv.tbmmovel;
/*
RestRequest.java
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
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class RestRequest implements Cloneable {

    public interface Fetcher {
        String fetch(Connection connection) throws IOException;
    }

    public interface Connection {
        void execute() throws IOException;

        String getBody() throws IOException;

        int getResponseCode() throws IOException;

        String getHeader(String key) throws IOException;

        void close();

        RestRequest getRequest();
    }

    public static final Fetcher defaultFetcher = new Fetcher() {
        @Override
        public String fetch(Connection connection) throws IOException {
            connection.execute();
            return connection.getBody();
        }
    };

    private String auth;
    private String method;
    private Object body;
    private URL baseUrl;
    private URI uri = URI.create("");
    private Fetcher fetcher = defaultFetcher;
    private SSLContext sslContext;
    private int connectTimeout;
    private int readTimeout;
    private String ifNoneMatch;
    private int xWaitChange;

    public Fetcher getFetcher() {
        return fetcher;
    }

    public void setFetcher(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    public void setAuth(String username, String password) {
        try {
            auth = "Basic " + Base64.encodeToString(
                    (username + ':' + password).getBytes("UTF-8"),
                    Base64.NO_WRAP);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public void toUri(URI uri) {
        this.uri = this.uri.resolve(uri);
    }

    public void toUri(String uri) {
        this.uri = this.uri.resolve(uri);
    }

    public void setIfNoneMatch(String etag) {
        this.ifNoneMatch = etag;
    }

    public void setWaitChange(int timeout) {
        this.xWaitChange = timeout;
    }

    public void setBaseUrl(URL baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public void setConnectTimeout(int timeout) {
        connectTimeout = timeout;
    }

    public void setReadTimeout(int timeout) {
        readTimeout = timeout;
    }

    @Override
    public RestRequest clone() {
        try {
            return (RestRequest) super.clone();
        } catch (CloneNotSupportedException|ClassCastException e) {
            throw new AssertionError(e);
        }
    }

    public Connection createConnection() throws IOException {
        final HttpURLConnection connection = openConnection(uri);
        prepareConnection(connection);
        return new Connection() {
            @Override
            public void execute() throws IOException {
                connection.connect();
                if (body != null) {
                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(body.toString().getBytes("UTF-8"));
                    outputStream.flush();
                }
            }

            @Override
            public String getBody() throws IOException {
                int responseCode = getResponseCode();
                if (responseCode >= 400) {
                    throw new HttpError(responseCode);
                }

                return readContent(connection);
            }

            @Override
            public int getResponseCode() throws IOException {
                return connection.getResponseCode();
            }

            @Override
            public String getHeader(String key) throws IOException {
                return connection.getHeaderField(key);
            }

            @Override
            public void close() {
                connection.disconnect();
            }

            @Override
            public RestRequest getRequest() {
                return RestRequest.this;
            }
        };
    }

    public String fetch() throws IOException {
        Connection connection = createConnection();
        try {
            return fetcher.fetch(connection);
        } finally {
            connection.close();
        }
    }

    public JSONObject fetchJson() throws IOException, JSONException {
        String result = fetch();
        if (result == null) {
            return null;
        }
        JSONTokener jsonTokener = new JSONTokener(result);
        Object jsonValue = jsonTokener.nextValue();
        try {
            return (JSONObject) jsonValue;
        } catch (ClassCastException e) {
            throw new JSONException("Response not a JSONObject: " + jsonValue);
        }
    }

    protected URL createURL(URI uri) throws MalformedURLException {
        return new URL(baseUrl, uri.toString());
    }

    protected HttpURLConnection openConnection(URI uri) throws IOException {
        URL url = createURL(uri);
        URLConnection urlConnection = url.openConnection();
        if (sslContext != null) {
            HttpsURLConnection connection;
            try {
                connection = (HttpsURLConnection) urlConnection;
            } catch (ClassCastException e) {
                throw new IOException("Not an HTTPS URL: " + url);
            }
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            return connection;
        } else {
            try {
                return (HttpURLConnection) urlConnection;
            } catch (ClassCastException e) {
                throw new IOException("Not an HTTP(S) URL: " + url);
            }
        }
    }

    protected void prepareConnection(HttpURLConnection connection) throws IOException {
        if (body != null) {
            connection.setDoOutput(true);
        }
        if (auth != null) {
            connection.setRequestProperty("Authorization", auth);
        }
        if (method != null) {
            connection.setRequestMethod(method);
        }
        if (ifNoneMatch != null) {
            connection.setRequestProperty("If-None-Match", ifNoneMatch);
            if (xWaitChange > 0) {
                connection.setRequestProperty("X-Wait-Change", Integer.toString(xWaitChange));
            }
        }

        // timeouts default to 0, both here and in URLConnection
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
    }

    protected String readContent(HttpURLConnection connection) throws IOException {
        int len = connection.getContentLength();
        if (len <= 0) {
            return null;
        }
        InputStream inputStream = connection.getInputStream();
        byte[] content = new byte[len];
        int pos = 0;
        while (pos < len) {
            int bytesRead = inputStream.read(content, pos, len - pos);
            if (bytesRead < 0) {
                throw new EOFException();
            }
            pos += bytesRead;
        }
        return new String(content, "UTF-8");
    }
}
