package net.tbmcv.tbmmovel;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockJrcRequestBuilder implements JsonRestClient.RequestBuilder {
    private final Map<String, Object> params = new HashMap<>();
    private final Fetcher fetcher;

    public static Fetcher mockDefaultClient() {
        final JsonRestClient mockClient = mock(JsonRestClient.class);
        final Fetcher mockFetcher = mock(MockJrcRequestBuilder.Fetcher.class);
        when(mockClient.buildRequest()).then(new Answer<JsonRestClient.RequestBuilder>() {
            @Override
            public JsonRestClient.RequestBuilder answer(InvocationOnMock invocation)
                    throws Throwable {
                return new MockJrcRequestBuilder(mockFetcher);
            }
        });
        JsonRestClientFactory.Default.set(new JsonRestClientFactory() {
            @Override
            public JsonRestClient getRestClient(Context context) {
                return mockClient;
            }
        });
        return mockFetcher;
    }

    public MockJrcRequestBuilder(Fetcher fetcher) {
        this.fetcher = fetcher;
        params.put("uri", URI.create(""));
        params.put("method", "GET");
    }

    @Override
    public JSONObject fetch() throws IOException, JSONException {
        return fetcher.fetch(params);
    }

    public Fetcher getFetcher() {
        return fetcher;
    }

    public interface Fetcher {
        JSONObject fetch(Map<String, ?> params) throws IOException, JSONException;
    }

    @Override
    public MockJrcRequestBuilder auth(String username, String password) {
        params.put("username", username);
        params.put("password", password);
        return this;
    }

    @Override
    public MockJrcRequestBuilder method(String method) {
        params.put("method", method);
        return this;
    }

    @Override
    public MockJrcRequestBuilder body(JSONObject body) {
        params.put("body", body);
        return this;
    }

    @Override
    public MockJrcRequestBuilder toUri(URI uri) {
        params.put("uri", ((URI) params.get("uri")).resolve(uri));
        return this;
    }

    @Override
    public MockJrcRequestBuilder toUri(String uri) {
        return toUri(URI.create(uri));
    }
}
