package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.CallSuper;
import android.test.ActivityUnitTestCase;
import android.widget.EditText;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class BaseActivityUnitTest<A extends Activity> extends ActivityUnitTestCase<A> {
    private final Class<A> activityClass;
    private StartServiceTrapContextWrapper contextWrapper;

    public BaseActivityUnitTest(Class<A> activityClass) {
        super(activityClass);
        this.activityClass = activityClass;
    }

    @CallSuper
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        contextWrapper = new StartServiceTrapContextWrapper(getInstrumentation().getTargetContext());
        setActivityContext(contextWrapper);
    }

    protected static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("Timeout", latch.await(2, TimeUnit.SECONDS));
    }

    protected static void await(AnswerPromise<?> promise) throws InterruptedException {
        await(promise.getCallLatch());
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
