package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.test.ServiceTestCase;
import android.test.mock.MockContentResolver;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BaseIntentServiceUnitTest<S extends IntentService> extends ServiceTestCase<S> {
    static final String LOG_TAG = "BaseIntentServiceUTest";

    private volatile Intent lastActivityIntent;
    private MockContentResolver contentResolver;
    private Map<Intent, CountDownLatch> processingIntents;

    public BaseIntentServiceUnitTest(Class<S> serviceClass) {
        super(serviceClass);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        contentResolver = new MockContentResolver();
        processingIntents = Collections.synchronizedMap(new HashMap<Intent, CountDownLatch>());
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

        @Override
        public ContentResolver getContentResolver() {
            return contentResolver;
        }

        BaseIntentServiceUnitTest<S> getCurrentTest() {
            return BaseIntentServiceUnitTest.this;
        }
    }

    protected StartServiceTrapContextWrapper getStartServiceTrap() {
        return (StartServiceTrapContextWrapper) getContext();
    }

    protected void setIntentHandled(Intent intent) {
        CountDownLatch latch = processingIntents.get(intent);
        if (latch != null) {
            if (latch.getCount() == 0) {
                Log.w(LOG_TAG, "Latch already counted down for intent " + intent);
            }
            latch.countDown();
        } else {
            Log.w(LOG_TAG, "No latch for intent " + intent);
        }
    }

    public static void setIntentHandled(Context context, Intent intent) {
        ((BaseIntentServiceUnitTest<?>.TestingContext) context)
                .getCurrentTest().setIntentHandled(intent);
    }

    @Override
    protected void startService(Intent intent) {
        processingIntents.put(intent, new CountDownLatch(1));
        super.startService(intent);
    }

    protected CountDownLatch sendServiceIntent(Intent intent) {
        startService(intent);
        return processingIntents.get(intent);
    }

    protected void sendServiceIntentAndWait(Intent intent, long timeout, TimeUnit unit)
            throws InterruptedException {
        assertTrue("Not processed in time: " + intent,
                sendServiceIntent(intent).await(timeout, unit));
    }

    protected void sendServiceIntentAndWait(Intent intent) throws InterruptedException {
        sendServiceIntentAndWait(intent, 5, TimeUnit.SECONDS);
    }

    protected MockContentResolver getContentResolver() {
        return contentResolver;
    }

    protected Intent assertActivityStarted(Class<? extends Activity> cls) {
        Intent intent = lastActivityIntent;
        assertNotNull("No Activity started", intent);
        assertEquals(cls.getCanonicalName(), intent.getComponent().getClassName());
        assertTrue("Activity Intent doesn't have FLAG_ACTIVITY_NEW_TASK set",
                (intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        return intent;
    }

    protected Intent assertActivityStarted(Class<? extends Activity> cls,
                                           long timeout, TimeUnit unit)
            throws InterruptedException {
        long endTime = System.nanoTime() + unit.toNanos(timeout);
        while (lastActivityIntent == null) {
            long remaining = endTime - System.nanoTime();
            if (remaining <= 0) {
                break;
            } else if (remaining >= 1_000_000) {
                Thread.sleep(1);
            } else {
                Thread.sleep(0, (int) remaining);
            }
        }
        return assertActivityStarted(cls);
    }

    protected Intent startServiceAndWaitForBroadcast(Intent intent, String... broadcastActions)
            throws InterruptedException {
        Intent broadcastIntent = startServiceAndGetBroadcast(intent, broadcastActions);
        assertNotNull("No broadcast received", broadcastIntent);
        return broadcastIntent;
    }

    protected Intent startServiceAndGetBroadcast(Intent intent, String... broadcastActions)
            throws InterruptedException {
        final BlockingQueue<Intent> queue = new ArrayBlockingQueue<>(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                queue.offer(intent);
            }
        };
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(getContext());
        try {
            for (String action : broadcastActions) {
                broadcastManager.registerReceiver(receiver, new IntentFilter(action));
            }
            sendServiceIntentAndWait(intent);
            return queue.poll(100, TimeUnit.MILLISECONDS);
        } finally {
            broadcastManager.unregisterReceiver(receiver);
        }
    }
}
