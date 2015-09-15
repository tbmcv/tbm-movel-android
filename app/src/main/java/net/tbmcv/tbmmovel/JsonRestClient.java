package net.tbmcv.tbmmovel;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

public interface JsonRestClient {
    Request.Builder buildRequest();

    interface Request {
        JSONObject fetch() throws IOException;

        interface Builder {
            Builder auth(String username, String password);

            Builder method(String method);

            Builder body(JSONObject body);

            Builder toUri(URI uri);

            Builder toUri(String uri);

            Request build();
        }
    }
}
