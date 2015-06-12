package net.tbmcv.tbmmovel;

import android.net.Uri;
import android.support.annotation.Nullable;

import org.json.JSONObject;

public interface JsonRestClient {
    interface Callback {
        void onSuccess(JSONObject result);

        void onFailure(Exception err);
    }

    void setAuth(String username, String password);

    void fetch(String method, Uri uri, @Nullable JSONObject body, @Nullable Callback callback);
}
