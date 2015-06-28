package net.tbmcv.tbmmovel;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;

public class AnswerPromise<T> implements Answer<T> {
    private static final Map<AnswerPromise<?>, ?> instances = new WeakHashMap<>();

    private final CountDownLatch callLatch = new CountDownLatch(1);
    private boolean ready = false;
    private T result;
    private Throwable error;

    public AnswerPromise() {
        synchronized (instances) {
            instances.put(this, null);
        }
    }

    @Override
    public synchronized T answer(InvocationOnMock invocation) throws Throwable {
        callLatch.countDown();
        while (!isReady()) {
            wait();
        }
        if (error == null) {
            return result;
        } else {
            Throwable rethrown = error.getClass().newInstance();
            rethrown.initCause(error);
            if (error instanceof TestFinished) {
                ExceptionSwallower.swallow(Thread.currentThread(), rethrown);
            }
            throw rethrown;
        }
    }

    public CountDownLatch getCallLatch() {
        return callLatch;
    }

    public synchronized void setResult(@Nullable T result) {
        this.result = result;
        ready = true;
        notifyAll();
    }

    public synchronized void setError(@NonNull Throwable error) {
        this.error = error;
        ready = true;
        notifyAll();
    }

    public synchronized boolean isReady() {
        return ready;
    }

    public static class TestFinished extends RuntimeException { }

    private static class ExceptionSwallower implements Thread.UncaughtExceptionHandler {
        private final Map<Throwable, ?> throwables = new WeakHashMap<>();
        private final Thread.UncaughtExceptionHandler oldHandler;

        ExceptionSwallower(Thread.UncaughtExceptionHandler oldHandler) {
            this.oldHandler = oldHandler;
        }

        void addThrowable(Throwable throwable) {
            throwables.put(throwable, null);
        }

        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (throwables.containsKey(cause)) {
                    throwables.remove(cause);
                    if (throwables.isEmpty() && thread.getUncaughtExceptionHandler() == this) {
                        thread.setUncaughtExceptionHandler(oldHandler);
                    }
                    return;
                }
            }
            oldHandler.uncaughtException(thread, ex);
        }

        static void swallow(Thread thread, Throwable throwable) {
            Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
            ExceptionSwallower swallower;
            if (handler instanceof ExceptionSwallower) {
                swallower = (ExceptionSwallower) handler;
            } else {
                swallower = new ExceptionSwallower(handler);
                thread.setUncaughtExceptionHandler(swallower);
            }
            swallower.addThrowable(throwable);
        }
    }

    public static void cleanup() {
        synchronized (instances) {
            for (AnswerPromise<?> promise : instances.keySet()) {
                if (!promise.isReady()) {
                    promise.setError(new TestFinished());
                }
            }
            instances.clear();
        }
    }
}
