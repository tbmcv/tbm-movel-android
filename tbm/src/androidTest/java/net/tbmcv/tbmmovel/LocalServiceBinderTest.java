package net.tbmcv.tbmmovel;

import android.app.Service;

import junit.framework.TestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class LocalServiceBinderTest extends TestCase {
    Service mockService;
    LocalServiceListener<Service> mockListener, mockListener2, mockListener3;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockService = mock(Service.class);
        mockListener = mock(LocalServiceListener.class);
        mockListener2 = mock(LocalServiceListener.class);
        mockListener3 = mock(LocalServiceListener.class);
    }

    public void testIsReady() {
        LocalServiceBinder<Service> binder = new LocalServiceBinder<>(mockService, false);
        assertFalse(binder.isReady());
        binder.setReady();
        assertTrue(binder.isReady());
        binder.setReady();
        assertTrue(binder.isReady());
    }

    void checkIsReadyConstructedReady(LocalServiceBinder<Service> binder) {
        assertTrue(binder.isReady());
        binder.setReady();
        assertTrue(binder.isReady());
    }

    public void testIsReadyConstructedReady() {
        checkIsReadyConstructedReady(new LocalServiceBinder<>(mockService, true));
    }

    public void testIsReadyConstructedDefault() {
        checkIsReadyConstructedReady(new LocalServiceBinder<>(mockService));
    }

    void checkCallbackConstructedReady(LocalServiceBinder<Service> binder) {
        binder.connectWhenReady(mockListener);
        verify(mockListener).serviceConnected(mockService);
        binder.connectWhenReady(mockListener2);
        verify(mockListener2).serviceConnected(mockService);
    }

    public void testCallbackConstructedReady() {
        checkCallbackConstructedReady(new LocalServiceBinder<>(mockService, true));
    }

    public void testCallbackConstructedDefault() {
        checkCallbackConstructedReady(new LocalServiceBinder<>(mockService));
    }

    public void testCallbackAlreadyReady() {
        LocalServiceBinder<Service> binder = new LocalServiceBinder<>(mockService, false);
        binder.setReady();
        binder.connectWhenReady(mockListener);
        verify(mockListener).serviceConnected(mockService);
        binder.connectWhenReady(mockListener2);
        verify(mockListener2).serviceConnected(mockService);
    }

    public void testCallback() {
        LocalServiceBinder<Service> binder = new LocalServiceBinder<>(mockService, false);
        binder.connectWhenReady(mockListener);
        binder.connectWhenReady(mockListener2);
        verify(mockListener, never()).serviceConnected(mockService);
        verify(mockListener2, never()).serviceConnected(mockService);
        binder.setReady();
        verify(mockListener).serviceConnected(mockService);
        verify(mockListener2).serviceConnected(mockService);
        binder.connectWhenReady(mockListener3);
        verify(mockListener3).serviceConnected(mockService);
    }
}
