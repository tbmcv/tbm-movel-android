package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.CallSuper;
import android.test.ServiceTestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BaseServiceUnitTest<S extends Service> extends ServiceTestCase<S> {
    private final Class<S> serviceClass;
    private volatile Intent lastActivityIntent;
    protected Pauser mockPauser;

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

    protected Semaphore preparePause() {
        final Semaphore semaphore = new Semaphore(1);
        AnswerPromise<?> pausePromise = new AnswerPromise<>();
        try {
            doAnswer(new Answer() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    semaphore.acquire();
                    return null;
                }
            }).when(mockPauser).pause(anyInt(), any(TimeUnit.class));
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        return semaphore;
    }

    protected long verifyPauseMillis(VerificationMode mode) {
        ArgumentCaptor<Long> duration = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unit = ArgumentCaptor.forClass(TimeUnit.class);
        try {
            verify(mockPauser, mode).pause(duration.capture(), unit.capture());
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        return unit.getValue().toMillis(duration.getValue());
    }

    protected long verifyPauseMillis() {
        return verifyPauseMillis(times(1));
    }

    @CallSuper
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockPauser = mock(Pauser.class);
        setContext(spy(new TestingContext(getContext())));
    }

    @CallSuper
    @Override
    protected void tearDown() throws Exception {
        AnswerPromise.cleanup();
        super.tearDown();
    }

    protected class TestingContext extends StartServiceTrapContextWrapper {
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
