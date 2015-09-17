package net.tbmcv.tbmmovel;

import org.mockito.InOrder;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.SecureRandom;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class HttpsJsonRestClientTest extends HttpJsonRestClientTest {
    protected SSLContext mockSSLContext;
    protected SSLContextSpi mockSSLContextSpi;
    protected SSLSocketFactory mockSSLSocketFactory;
    private HttpsURLConnection mockHttpsConnection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockHttpsConnection = mock(HttpsURLConnection.class);
        mockSSLSocketFactory = mock(SSLSocketFactory.class);
        mockSSLContextSpi = new SSLContextSpi() {
            @Override
            protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr)
                    throws KeyManagementException {
                throw new UnsupportedOperationException();
            }

            @Override
            protected SSLSocketFactory engineGetSocketFactory() {
                return mockSSLSocketFactory;
            }

            @Override
            protected SSLServerSocketFactory engineGetServerSocketFactory() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected SSLEngine engineCreateSSLEngine(String host, int port) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected SSLEngine engineCreateSSLEngine() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected SSLSessionContext engineGetServerSessionContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected SSLSessionContext engineGetClientSessionContext() {
                throw new UnsupportedOperationException();
            }
        };
        mockSSLContext = new SSLContext(mockSSLContextSpi, null, null) { };
    }

    @Override
    protected HttpsURLConnection getMockConnection() {
        return mockHttpsConnection;
    }

    @Override
    protected HttpsJsonRestClient createClient(URL baseUrl) {
        return new HttpsJsonRestClient(baseUrl, mockSSLContext) {
            @Override
            protected URL createURL(URI uri) throws MalformedURLException {
                return super.createURL(uri);
            }
        };
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://www.example.org/";
    }

    public void testContextSocketFactoryUsed() throws Exception {
        setResponse("");
        createClient().buildRequest().fetch();
        HttpsURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setSSLSocketFactory(mockSSLSocketFactory);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
    }
}
