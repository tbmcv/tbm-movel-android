package net.tbmcv.tbmmovel;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

public interface JsonRestClient {
    RequestBuilder buildRequest();

    interface RequestBuilder {
        JSONObject fetch() throws IOException, JSONException;

        RequestBuilder auth(String username, String password);

        RequestBuilder method(String method);

        RequestBuilder body(JSONObject body);

        RequestBuilder toUri(URI uri);

        RequestBuilder toUri(String uri);
    }
}
