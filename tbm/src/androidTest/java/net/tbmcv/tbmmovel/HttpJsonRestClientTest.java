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
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpJsonRestClientTest extends TestCase {
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

    protected HttpJsonRestClient createClient(URL baseUrl) {
        return new HttpJsonRestClient(baseUrl) {
            @Override
            protected URL createURL(URI uri) throws MalformedURLException {
                return super.createURL(uri);
            }
        };
    }

    protected HttpJsonRestClient createClient(String baseUrl) throws MalformedURLException {
        return createClient(createBaseUrl(baseUrl));
    }

    protected HttpJsonRestClient createClient() {
        try {
            return createClient(getDefaultBaseUrl());
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
        createClient().buildRequest().auth("hi", "mom").fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setRequestProperty("Authorization", "Basic aGk6bW9t");
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
    }

    public void testSetBody() throws Exception {
        JSONObject body = new JSONObject()
                .put("x", 3)
                .put("y", new JSONArray().put("a").put("b"));
        setResponse("");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HttpURLConnection mockConnection = getMockConnection();
        when(mockConnection.getOutputStream()).thenReturn(outputStream);
        createClient().buildRequest().body(body).fetch();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setDoOutput(true);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(body.toString(), new String(outputStream.toByteArray(), "UTF-8"));
    }

    public void testSetMethod() throws Exception {
        String method = "DELETE";
        setResponse("");
        createClient().buildRequest().method(method).fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setRequestMethod(method);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
    }

    public void testToUriRelativeLeaf() throws Exception {
        setResponse("");
        createClient().buildRequest().toUri("abc").fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(getDefaultBaseUrl() + "abc", connectionUrl.toString());
    }

    public void testToUriRelativePath() throws Exception {
        setResponse("");
        createClient().buildRequest().toUri("abc/").fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(getDefaultBaseUrl() + "abc/", connectionUrl.toString());
    }

    public void testToUriAbsolute() throws Exception {
        setResponse("");
        createClient().buildRequest().toUri("abc/").toUri("/def/ghi").fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(getDefaultBaseUrl() + "def/ghi", connectionUrl.toString());
    }

    public void testToUriMultiple() throws Exception {
        setResponse("");
        createClient().buildRequest().toUri("abc/").toUri("def/").toUri("123").fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
        assertEquals(getDefaultBaseUrl() + "abc/def/123", connectionUrl.toString());
    }

    public void testSetConnectTimeout() throws Exception {
        final int timeout = 12345;
        setResponse("");
        createClient().buildRequest().connectTimeout(timeout).fetch();
        HttpURLConnection mockConnection = getMockConnection();
        InOrder inOrder = inOrder(mockConnection);
        inOrder.verify(mockConnection).setConnectTimeout(timeout);
        inOrder.verify(mockConnection).connect();
        inOrder.verify(mockConnection).disconnect();
    }

    public void testSetReadTimeout() throws Exception {
        final int timeout = 54321;
        setResponse("");
        createClient().buildRequest().readTimeout(timeout).fetch();
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
                createClient().buildRequest().fetch();
                fail("Didn't return HTTP error " + code);
            } catch (HttpError e) {
                assertEquals(code, e.getResponseCode());
            }
        }
    }
}
