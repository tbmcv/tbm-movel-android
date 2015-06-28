package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.test.ServiceTestCase;
import android.test.mock.MockContentResolver;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class BaseServiceUnitTest<S extends Service> extends ServiceTestCase<S> {
    private volatile Intent lastActivityIntent;
    private MockContentResolver contentResolver;

    public BaseServiceUnitTest(Class<S> serviceClass) {
        super(serviceClass);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        contentResolver = new MockContentResolver();
        setContext(new ContextWrapper(getContext()) {
            @Override
            public void startActivity(Intent intent) {
                lastActivityIntent = intent;
            }

            @Override
            public ContentResolver getContentResolver() {
                return contentResolver;
            }
        });
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
            startService(intent);
            Intent broadcastIntent = queue.poll(2, TimeUnit.SECONDS);
            assertNotNull("No broadcast received", broadcastIntent);
            return broadcastIntent;
        } finally {
            broadcastManager.unregisterReceiver(receiver);
        }
    }
}
