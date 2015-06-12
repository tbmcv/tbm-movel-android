package net.tbmcv.tbmmovel;

import android.app.Activity;

public class BaseActivity extends Activity {
    protected JsonRestClient getRestClient() {
        return JsonRestClientFactory.Default.get().getRestClient(this);
    }
}
