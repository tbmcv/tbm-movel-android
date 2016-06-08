package net.tbmcv.tbmmovel;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class LocalServiceConnectionTest extends TestCase {
    Service mockService;
    Context mockContext;
    LocalServiceListener<Service> mockListener, mockListener2;
    LocalServiceBinder<Service> mockBinder;
    LocalServiceConnection<Service> connection;
    ArgumentCaptor<ServiceConnection> connectionCaptor;
    ArgumentCaptor<LocalServiceListener<Service>> listenerCaptor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockService = mock(Service.class);
        mockContext = mock(Context.class);
        mockListener = mock(LocalServiceListener.class);
        mockListener2 = mock(LocalServiceListener.class);
        mockBinder = mock(LocalServiceBinder.class);
        connection = new LocalServiceConnection<>();
        connectionCaptor = ArgumentCaptor.forClass(ServiceConnection.class);
        listenerCaptor = ArgumentCaptor.forClass(
                (Class<LocalServiceListener<Service>>) (Class<?>) LocalServiceListener.class);
        //noinspection WrongConstant
        when(mockContext.bindService(any(Intent.class), any(ServiceConnection.class), anyInt()))
                .thenReturn(true);
    }

    public void testBindConnect() {
        connection.addListener(mockListener);
        Intent intent = new Intent();
        int flags = Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND;
        connection.bind(mockContext, intent, flags);
        //noinspection WrongConstant
        verify(mockContext).bindService(eq(intent), connectionCaptor.capture(), eq(flags));
        ServiceConnection sc = connectionCaptor.getValue();
        sc.onServiceConnected(null, mockBinder);
        verify(mockBinder).connectWhenReady(listenerCaptor.capture());
        LocalServiceListener<Service> listener = listenerCaptor.getValue();
        connection.addListener(mockListener2);

        assertFalse(connection.isBound());
        verifyZeroInteractions(mockListener);
        verifyZeroInteractions(mockListener2);

        listener.serviceConnected(mockService);

        assertTrue(connection.isBound());
        assertEquals(mockService, connection.getService());
        verify(mockListener).serviceConnected(mockService);
        verify(mockListener2).serviceConnected(mockService);
    }

    public void testDisconnect() {
        connection.addListener(mockListener);
        Intent intent = new Intent();
        int flags = Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND;
        connection.bind(mockContext, intent, flags);
        //noinspection WrongConstant
        verify(mockContext).bindService(eq(intent), connectionCaptor.capture(), eq(flags));
        ServiceConnection sc = connectionCaptor.getValue();
        sc.onServiceConnected(null, mockBinder);
        verify(mockBinder).connectWhenReady(listenerCaptor.capture());
        LocalServiceListener<Service> listener = listenerCaptor.getValue();
        connection.addListener(mockListener2);
        listener.serviceConnected(mockService);

        verify(mockListener, never()).serviceDisconnected();
        verify(mockListener2, never()).serviceDisconnected();

        sc.onServiceDisconnected(null);

        assertFalse(connection.isBound());
        verify(mockListener).serviceDisconnected();
        verify(mockListener2).serviceDisconnected();
    }
}
