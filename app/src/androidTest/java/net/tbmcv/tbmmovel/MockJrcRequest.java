package net.tbmcv.tbmmovel;

import android.content.Context;

import org.json.JSONObject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockJrcRequest implements JsonRestClient.Request {
    private final Map<String, Object> params;
    private final Fetcher fetcher;

    public static Fetcher mockDefaultClient() {
        final JsonRestClient mockClient = mock(JsonRestClient.class);
        final Fetcher mockFetcher = mock(MockJrcRequest.Fetcher.class);
        when(mockClient.buildRequest()).then(new Answer<JsonRestClient.Request.Builder>() {
            @Override
            public JsonRestClient.Request.Builder answer(InvocationOnMock invocation) throws Throwable {
                return new Builder(new MockJrcRequest(mockFetcher));
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

    public MockJrcRequest(Fetcher fetcher) {
        this.fetcher = fetcher;
        Map<String, Object> values = new HashMap<>();
        values.put("uri", URI.create(""));
        values.put("method", "GET");
        this.params = Collections.unmodifiableMap(values);
    }

    private MockJrcRequest(Fetcher fetcher, Map<String, ?> params) {
        this.fetcher = fetcher;
        this.params = Collections.unmodifiableMap(new HashMap<>(params));
    }

    @Override
    public JSONObject fetch() throws IOException {
        return fetcher.fetch(params);
    }

    public Fetcher getFetcher() {
        return fetcher;
    }

    public interface Fetcher {
        JSONObject fetch(Map<String, ?> params) throws IOException;
    }

    private static class Builder implements JsonRestClient.Request.Builder {
        private final Map<String, Object> params;
        private final Fetcher fetcher;

        private Builder(MockJrcRequest request) {
            this.fetcher = request.fetcher;
            this.params = new HashMap<>(request.params);
        }

        @Override
        public JsonRestClient.Request.Builder auth(String username, String password) {
            params.put("username", username);
            params.put("password", password);
            return this;
        }

        @Override
        public JsonRestClient.Request.Builder method(String method) {
            params.put("method", method);
            return this;
        }

        @Override
        public JsonRestClient.Request.Builder body(JSONObject body) {
            params.put("body", body);
            return this;
        }

        @Override
        public JsonRestClient.Request.Builder toUri(URI uri) {
            params.put("uri", ((URI) params.get("uri")).resolve(uri));
            return this;
        }

        @Override
        public JsonRestClient.Request.Builder toUri(String uri) {
            return toUri(URI.create(uri));
        }

        @Override
        public JsonRestClient.Request build() {
            return new MockJrcRequest(fetcher, params);
        }
    }
}
