package net.tbmcv.tbmmovel;

import android.content.Context;

public interface JsonRestClientFactory {
    JsonRestClient getRestClient(Context context);

    final class Default {
        private static JsonRestClientFactory factory;

        public static JsonRestClientFactory get() {
            return factory;
        }

        static void set(JsonRestClientFactory factory) {
            Default.factory = factory;
        }
    }
}
