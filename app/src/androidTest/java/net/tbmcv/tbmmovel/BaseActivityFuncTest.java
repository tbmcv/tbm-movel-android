package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.EditText;

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
        setActivityInitialTouchMode(true);
    }

    protected JsonRestClient getRestClient() {
        return mockClient;
    }

    protected void enterText(int viewId, String text) {
        final EditText editText = (EditText) getActivity().findViewById(viewId);
        assertTrue("Text entry not shown", editText.isShown());
        assertTrue("Text entry not enabled", editText.isEnabled());
        assertTrue("Text entry not focusable", editText.isFocusable());
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                editText.requestFocus();
            }
        });
        getInstrumentation().waitForIdleSync();
        getInstrumentation().sendStringSync(text);
        getInstrumentation().waitForIdleSync();
        assertEquals(text, editText.getText().toString());
    }
}
