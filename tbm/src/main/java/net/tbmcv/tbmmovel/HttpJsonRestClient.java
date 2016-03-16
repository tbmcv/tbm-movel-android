package net.tbmcv.tbmmovel;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

public class HttpJsonRestClient implements JsonRestClient {
    private final URL baseUrl;

    public HttpJsonRestClient(URL baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public JsonRestClient.RequestBuilder buildRequest() {
        return new RequestBuilder();
    }

    protected URL createURL(URI uri) throws MalformedURLException {
        return new URL(getBaseUrl(), uri.toString());
    }

    protected HttpURLConnection openConnection(URI uri) throws IOException {
        URLConnection connection = createURL(uri).openConnection();
        try {
            return (HttpURLConnection) connection;
        } catch (ClassCastException e) {
            throw new IOException("Not an HTTP(S) URL");
        }
    }

    public URL getBaseUrl() {
        return baseUrl;
    }

    private class RequestBuilder implements JsonRestClient.RequestBuilder {
        private String mAuth;
        private String mMethod;
        private String mBody;
        private URI mUri = URI.create("");
        private int mConnectTimeout;
        private int mReadTimeout;

        @Override
        public RequestBuilder auth(String username, String password) {
            try {
                mAuth = "Basic " + Base64.encodeToString(
                        (username + ':' + password).getBytes("UTF-8"),
                        Base64.NO_WRAP);
            } catch (UnsupportedEncodingException e) {
                throw new Error(e);
            }
            return this;
        }

        @Override
        public RequestBuilder method(String method) {
            mMethod = method;
            return this;
        }

        @Override
        public RequestBuilder body(JSONObject body) {
            mBody = body.toString();
            return this;
        }

        @Override
        public RequestBuilder toUri(URI uri) {
            mUri = mUri.resolve(uri);
            return this;
        }

        @Override
        public RequestBuilder toUri(String uri) {
            return toUri(URI.create(uri));
        }

        @Override
        public RequestBuilder connectTimeout(int timeout) {
            mConnectTimeout = timeout;
            return this;
        }

        @Override
        public RequestBuilder readTimeout(int timeout) {
            mReadTimeout = timeout;
            return this;
        }

        @Override
        public JSONObject fetch() throws IOException, JSONException {
            HttpURLConnection connection = openConnection(mUri);
            if (mBody != null) {
                connection.setDoOutput(true);
            }
            if (mAuth != null) {
                connection.setRequestProperty("Authorization", mAuth);
            }
            if (mMethod != null) {
                connection.setRequestMethod(mMethod);
            }

            // timeouts default to 0, both here and in URLConnection
            connection.setConnectTimeout(mConnectTimeout);
            connection.setReadTimeout(mReadTimeout);

            connection.connect();
            try {
                if (mBody != null) {
                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(mBody.getBytes("UTF-8"));
                    outputStream.flush();
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 400) {
                    throw new HttpError(responseCode);
                }

                String response = readInputStreamToString(
                        connection.getInputStream(), "UTF-8").trim();
                if (response.length() == 0) {
                    return null;
                }
                JSONTokener jsonTokener = new JSONTokener(response);
                Object jsonValue = jsonTokener.nextValue();
                try {
                    return (JSONObject) jsonValue;
                } catch (ClassCastException e) {
                    throw new JSONException("Response not a JSONObject: " + jsonValue);
                }
            } finally {
                connection.disconnect();
            }
        }
    }

    private static String readInputStreamToString(InputStream inputStream, String encoding)
            throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int count;
        while ((count = inputStream.read(buffer)) >= 0) {
            byteStream.write(buffer, 0, count);
        }
        return new String(byteStream.toByteArray(), encoding);
    }
}
