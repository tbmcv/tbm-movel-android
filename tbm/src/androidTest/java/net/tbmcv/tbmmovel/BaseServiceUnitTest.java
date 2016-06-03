package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.test.ServiceTestCase;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BaseServiceUnitTest<S extends Service> extends ServiceTestCase<S> {
    private final Class<S> serviceClass;
    private volatile Intent lastActivityIntent;

    public BaseServiceUnitTest(Class<S> serviceClass) {
        super(serviceClass);
        this.serviceClass = serviceClass;
    }

    protected static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("Timeout", latch.await(2, TimeUnit.SECONDS));
    }

    protected static void await(AnswerPromise<?> promise) throws InterruptedException {
        await(promise.getCallLatch());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setContext(new TestingContext(getContext()));
    }

    private class TestingContext extends StartServiceTrapContextWrapper {
        TestingContext(Context base) {
            super(base);
        }

        @Override
        public void startActivity(Intent intent) {
            lastActivityIntent = intent;
        }
    }

    protected StartServiceTrapContextWrapper getStartServiceTrap() {
        return (StartServiceTrapContextWrapper) getContext();
    }

    protected Intent assertActivityStarted(Class<? extends Activity> cls) {
        Intent intent = lastActivityIntent;
        assertNotNull("No Activity started", intent);
        assertEquals(cls.getCanonicalName(), intent.getComponent().getClassName());
        assertTrue("Activity Intent doesn't have FLAG_ACTIVITY_NEW_TASK set",
                (intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        return intent;
    }

    protected void bindService() {
        bindService(new Intent(getContext(), serviceClass));
    }

    protected static void assertUriEquals(URI expected, Object actual) {
        assertEquals(expected, URI.create("/").resolve((URI) actual));
    }

    protected static void assertUriEquals(String expected, Object actual) {
        assertUriEquals(URI.create(expected), actual);
    }
}
