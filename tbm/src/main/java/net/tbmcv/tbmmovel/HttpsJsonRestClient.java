package net.tbmcv.tbmmovel;

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
