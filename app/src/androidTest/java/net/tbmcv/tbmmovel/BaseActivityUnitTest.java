package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityUnitTestCase;

import static org.mockito.Mockito.mock;

public abstract class BaseActivityUnitTest<A extends Activity> extends ActivityUnitTestCase<A> {
    private final Class<A> activityClass;

    public BaseActivityUnitTest(Class<A> activityClass) {
        super(activityClass);
        this.activityClass = activityClass;
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
        startActivity(new Intent(getInstrumentation().getTargetContext(), activityClass),
                null, null);
    }

    protected JsonRestClient getRestClient() {
        return mockClient;
    }

    protected void assertLaunched(Class<? extends Activity> activityClass) {
        Intent launched = getStartedActivityIntent();
        assertNotNull("No activity started", launched);
        Context context = getInstrumentation().getTargetContext();
        assertEquals(
                new ComponentName(context, activityClass),
                launched.resolveActivity(context.getPackageManager()));
    }
}
