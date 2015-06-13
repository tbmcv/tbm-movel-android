package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;

import static org.mockito.Mockito.mock;

public abstract class BaseActivityFuncTest<A extends Activity>
        extends ActivityInstrumentationTestCase2<A> {
    @SuppressWarnings("deprecation")
    public BaseActivityFuncTest(Class<A> activityClass) {
        // Use deprecated constructor for compatibility with API level 7
        super(activityClass.getPackage().getName(), activityClass);
    }

    JsonRestClient mockClient;

    @Override
    protected void setUp() throws Exception {
        mockClient = mock(JsonRestClient.class);
        JsonRestClientFactory.Default.set(new JsonRestClientFactory() {
            @Override
            public JsonRestClient getRestClient(Context context) {
                return mockClient;
            }
        });
        super.setUp();
    }

    protected JsonRestClient getRestClient() {
        return mockClient;
    }
}
