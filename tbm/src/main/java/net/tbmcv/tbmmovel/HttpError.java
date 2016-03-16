package net.tbmcv.tbmmovel;

import java.io.IOException;

public class HttpError extends IOException {
    private int responseCode;

    public HttpError(int responseCode) {
        super("HTTP error " + responseCode);
        this.responseCode = responseCode;
    }

    public int getResponseCode() {
        return responseCode;
    }
}
