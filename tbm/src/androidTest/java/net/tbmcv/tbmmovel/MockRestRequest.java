package net.tbmcv.tbmmovel;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.URI;

import static org.mockito.Mockito.when;

public class MockRestRequest extends RestRequest {
    private String username;
    private String password;
    private String method = "GET";
    private URI uri = URI.create("");
    private int connectTimeout;
    private int readTimeout;
    private Object body;

    public static void mockTbmApi(TbmApiService mockService, final Fetcher fetcher) {
        when(mockService.createRequest()).thenAnswer(new Answer<RestRequest>() {
            @Override
            public RestRequest answer(InvocationOnMock invocation) throws Throwable {
                RestRequest request = new MockRestRequest();
                request.setFetcher(fetcher);
                return request;
            }
        });
    }

    @Override
    public void setAuth(String username, String password) {
        super.setAuth(username, password);
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public void setMethod(String method) {
        super.setMethod(method);
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    @Override
    public void toUri(URI uri) {
        super.toUri(uri);
        this.uri = this.uri.resolve(uri);
    }

    @Override
    public void toUri(String uri) {
        super.toUri(uri);
        this.uri = this.uri.resolve(uri);
    }

    public URI getUri() {
        return uri;
    }

    @Override
    public void setConnectTimeout(int timeout) {
        super.setConnectTimeout(timeout);
        this.connectTimeout = timeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public void setReadTimeout(int timeout) {
        super.setReadTimeout(timeout);
        this.readTimeout = timeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    @Override
    public void setBody(Object body) {
        super.setBody(body);
        this.body = body;
    }

    public Object getBody() {
        return body;
    }
}
