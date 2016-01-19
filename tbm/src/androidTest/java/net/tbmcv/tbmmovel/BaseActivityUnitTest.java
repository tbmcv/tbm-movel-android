package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.test.ActivityUnitTestCase;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseActivityUnitTest<A extends Activity> extends ActivityUnitTestCase<A> {
    private final Class<A> activityClass;
    private StartServiceTrapContextWrapper contextWrapper;

    public BaseActivityUnitTest(Class<A> activityClass) {
        super(activityClass);
        this.activityClass = activityClass;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        contextWrapper = new StartServiceTrapContextWrapper(getInstrumentation().getTargetContext());
        setActivityContext(contextWrapper);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected StartServiceTrapContextWrapper getStartServiceTrap() {
        return contextWrapper;
    }

    protected void launch() {
        startActivity(new Intent(getInstrumentation().getTargetContext(), activityClass),
                null, null);
        getInstrumentation().waitForIdleSync();
    }

    protected void assertLaunched(Class<? extends Activity> activityClass) {
        Intent launched = getStartedActivityIntent();
        assertNotNull("No activity started", launched);
        Context context = getInstrumentation().getTargetContext();
        assertEquals(
                new ComponentName(context, activityClass),
                launched.resolveActivity(context.getPackageManager()));
    }

    protected void enterText(int viewId, String text) {
        final EditText editText = (EditText) getActivity().findViewById(viewId);
        assertTrue("Text entry not enabled", editText.isEnabled());
        assertTrue("Text entry not focusable", editText.isFocusable());
        editText.setText(text);
    }
}
