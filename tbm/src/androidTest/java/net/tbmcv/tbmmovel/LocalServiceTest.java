package net.tbmcv.tbmmovel;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LocalServiceTest extends TestCase {

    public static class TestService extends Service {
        static class Binder extends LocalServiceBinder<TestService> {
            public Binder(TestService service) {
                super(service, false);
            }
        }

        final Binder binder = new Binder(this);

        @Override
        public IBinder onBind(Intent intent) {
            return binder;
        }
    }

    LocalServiceConnection<TestService> localConnection;
    Context mockContext;
    ArgumentCaptor<ServiceConnection> connectionCaptor;
    TestService service;

    protected ServiceConnection setupAndBind() {
        localConnection = new LocalServiceConnection<>();
        mockContext = mock(Context.class);
        localConnection.bind(mockContext, TestService.class);
        connectionCaptor = ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mockContext).bindService(any(Intent.class), connectionCaptor.capture(), anyInt());
        service = new TestService();
        ServiceConnection serviceConnection = connectionCaptor.getValue();
        serviceConnection.onServiceConnected(null, service.onBind(null));
        return serviceConnection;
    }

    public void testBind() {
        setupAndBind();
        assertFalse(localConnection.isBound());
        service.binder.setReady();
        assertTrue(localConnection.isBound());
        assertEquals(service, localConnection.getService());
    }

    public void testUnbind() {
        ServiceConnection serviceConnection = setupAndBind();
        service.binder.setReady();
        localConnection.unbind(mockContext);
        verify(mockContext).unbindService(serviceConnection);
        serviceConnection.onServiceDisconnected(null);
        assertFalse(localConnection.isBound());
    }
}
