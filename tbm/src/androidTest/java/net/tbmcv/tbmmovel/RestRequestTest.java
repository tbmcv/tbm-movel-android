package net.tbmcv.tbmmovel;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mockito.InOrder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
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
import static org.mockito.Mockito.when;

public class RestRequestTest extends TestCase {
    protected URLStreamHandler mockURLStreamHandler;
    protected URL connectionUrl;
    private HttpURLConnection mockHttpConnection;

    @Override
    protected void setUp() throws Exception {
        mockHttpConnection = mock(HttpURLConnection.class);
        mockURLStreamHandler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                connectionUrl = u;
                return getMockConnection();
            }
        };
    }

    protected HttpURLConnection getMockConnection() {
        return mockHttpConnection;
    }

    protected RestRequest createRequest(URL baseUrl) {
        RestRequest request = new RestRequest();
        request.setBaseUrl(baseUrl);
        return request;
    }

    protected RestRequest createRequest(String baseUrl) throws MalformedURLException {
        return createRequest(createBaseUrl(baseUrl));
    }

    protected RestRequest createRequest() {
        try {
            return createRequest(getDefaultBaseUrl());
        } catch (MalformedURLException e) {
            throw new Error(e);
        }
    }

    protected String getDefaultBaseUrl() {
        return "http://www.example.org/";
    }

    protected URL createBaseUrl(String url) throws MalformedURLException {
        return new URL(null, url, mockURLStreamHandler);
    }

    protected void setResponse(String response) throws IOException {
        when(getMockConnection().getInputStream()).thenReturn(
                new ByteArrayInputStream(response.getBytes("UTF-8")));
    }

    public void testSetAuth() throws Exception {
        setResponse("");
        RestRequest request = createRequest();
        request.setAuth("hi", "mom");
        request.fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setRequestProperty("Authorization", "Basic aGk6bW9t");
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
    }

    public void testSetBodyJson() throws Exception {
        JSONObject body = new JSONObject()
                .put("x", 3)
                .put("y", new JSONArray().put("a").put("b"));
        setResponse("");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HttpURLConnection mockConnection = getMockConnection();
        when(mockConnection.getOutputStream()).thenReturn(outputStream);
        RestRequest request = createRequest();
        request.setBody(body);
        request.fetch();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setDoOutput(true);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(body.toString(), new String(outputStream.toByteArray(), "UTF-8"));
    }

    public void testSetMethod() throws Exception {
        String method = "DELETE";
        setResponse("");
        RestRequest request = createRequest();
        request.setMethod(method);
        request.fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setRequestMethod(method);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
    }

    public void testToUriRelativeLeaf() throws Exception {
        setResponse("");
        RestRequest request = createRequest();
        request.toUri("abc");
        request.fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(getDefaultBaseUrl() + "abc", connectionUrl.toString());
    }

    public void testToUriRelativePath() throws Exception {
        setResponse("");
        RestRequest request = createRequest();
        request.toUri("abc/");
        request.fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(getDefaultBaseUrl() + "abc/", connectionUrl.toString());
    }

    public void testToUriAbsolute() throws Exception {
        setResponse("");
        RestRequest request = createRequest();
        request.toUri("abc/");
        request.toUri("/def/ghi");
        request.fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(getDefaultBaseUrl() + "def/ghi", connectionUrl.toString());
    }

    public void testToUriMultiple() throws Exception {
        setResponse("");
        RestRequest request = createRequest();
        request.toUri("abc/");
        request.toUri("def/");
        request.toUri("123");
        request.fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(getDefaultBaseUrl() + "abc/def/123", connectionUrl.toString());
    }

    public void testSetConnectTimeout() throws Exception {
        final int timeout = 12345;
        setResponse("");
        RestRequest request = createRequest();
        request.setConnectTimeout(timeout);
        request.fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setConnectTimeout(timeout);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
    }

    public void testSetReadTimeout() throws Exception {
        final int timeout = 54321;
        setResponse("");
        RestRequest request = createRequest();
        request.setReadTimeout(timeout);
        request.fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setReadTimeout(timeout);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
    }

    public final void testHttpError() throws Exception {
        int[] codes = {401, 400, 500, 600, 404};
        for (int code : codes) {
            when(getMockConnection().getResponseCode()).thenReturn(code);
            try {
                createRequest().fetch();
                fail("Didn't return HTTP error " + code);
            } catch (HttpError e) {
                assertEquals(code, e.getResponseCode());
            }
        }
    }

    protected SSLContext createMockSSLContext(final SSLSocketFactory sslSocketFactory) {
        SSLContextSpi sslContextSpi = new SSLContextSpi() {
            @Override
            protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr)
                    throws KeyManagementException {
                throw new UnsupportedOperationException();
            }

            @Override
            protected SSLSocketFactory engineGetSocketFactory() {
                return sslSocketFactory;
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
        return new SSLContext(sslContextSpi, null, null) { };
    }

    public void testContextSocketFactoryUsed() throws Exception {
        setResponse("");
        SSLSocketFactory mockSSLSocketFactory = mock(SSLSocketFactory.class);
        HttpsURLConnection connection = mock(HttpsURLConnection.class);
        mockHttpConnection = connection;
        RestRequest request = createRequest("https://www.example.org/");
        request.setSslContext(createMockSSLContext(mockSSLSocketFactory));
        request.fetch();
        InOrder inOrder = inOrder(connection);
        inOrder.verify(connection).setSSLSocketFactory(mockSSLSocketFactory);
        inOrder.verify(connection).connect();
        inOrder.verify(connection).disconnect();
    }
}
