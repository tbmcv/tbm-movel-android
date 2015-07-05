package net.tbmcv.tbmmovel;

import org.json.JSONObject;

import java.net.URI;

public interface JsonRestClient {
    Request.Builder buildRequest();

    interface Request {
        Builder buildUpon();

        JSONObject fetch();

        interface Builder {
            Builder auth(String username, String password);

            Builder method(String method);

            Builder body(JSONObject body);

            Builder baseUri(URI uri);

            Builder toUri(URI uri);

            Builder toUri(String uri);

            Request build();
        }
    }
}