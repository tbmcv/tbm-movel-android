package net.tbmcv.tbmmovel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SaldoServiceTest extends BaseServiceUnitTest<SaldoService> {
    public SaldoServiceTest() {
        super(SaldoService.class);
    }

    private AcctDataService mockAcctDataService;
    private AcctDataService.Binder acctDataBinder;
    private RestRequest.Fetcher mockFetcher;
    private ArgumentCaptor<MockRestRequest.Connection> requestCaptor;
    private BlockingQueue<Intent> broadcasts;
    private BroadcastReceiver receiver;

    protected void setUp() throws Exception {
        super.setUp();
        mockAcctDataService = mock(AcctDataService.class);
        acctDataBinder = new AcctDataService.Binder(mockAcctDataService);
        getStartServiceTrap().setBoundService(AcctDataService.class, acctDataBinder);
        mockFetcher = mock(RestRequest.Fetcher.class);
        MockRestRequest.mockAcctDataRequests(mockAcctDataService, mockFetcher);
        requestCaptor = ArgumentCaptor.forClass(MockRestRequest.Connection.class);
        final BlockingQueue<Intent> broadcastQueue = new LinkedBlockingQueue<>();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                broadcastQueue.add(intent);
            }
        };
        broadcasts = broadcastQueue;
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(
                receiver, new IntentFilter(SaldoService.ACTION_UPDATE));
        SaldoService.pauser = mockPauser;
    }

    @Override
    protected void bindService() {
        super.bindService();
        acctDataBinder.setReady();
    }

    @Override
    protected void tearDown() throws Exception {
        SaldoService.pauser = new RealPauser();
        try {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
        } finally {
            super.tearDown();
        }
    }

    public void testGetCredit() throws Exception {
        final int credit = 1999;
        when(mockAcctDataService.getAcctAuth()).thenReturn(new AuthPair("a", "b"));
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenReturn(
                new JSONObject().put("saldo", credit).toString());
        bindService();
        new AssertWaiter() {
            @Override
            protected void test() throws Exception {
                assertEquals(credit, getService().getCredit());
            }
        }.await();
    }

    public void testLocalBroadcast() throws Exception {
        final int credit = 73;
        when(mockAcctDataService.getAcctAuth()).thenReturn(new AuthPair("a", "b"));
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenReturn(
                new JSONObject().put("saldo", credit).toString());
        bindService();
        Intent intent = broadcasts.poll(2, TimeUnit.SECONDS);
        assertEquals(credit, intent.getIntExtra(
                SaldoService.EXTRA_CREDIT, SaldoService.UNKNOWN_CREDIT));
        assertEquals(credit, getService().getCredit());
    }

    public void testRequestSent() throws Exception {
        final String acctName = "c/9333333";
        final String pw = "anything";
        when(mockAcctDataService.getAcctAuth()).thenReturn(new AuthPair(acctName, pw));
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenReturn(
                new JSONObject().put("saldo", 1001).toString());
        preparePause();
        bindService();
        verify(mockFetcher, timeout(2000).atLeastOnce()).fetch(requestCaptor.capture());
        MockRestRequest mockRequest = requestCaptor.getValue().getRequest();
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(pw, mockRequest.getPassword());
        assertEquals("GET", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/saldo/", mockRequest.getUri());
    }

    public void testSecondRequestETag() throws Exception {
        final String acctName = "c/9222222";
        final String pw = "nothing";
        final String etag = "a1b2c3";
        when(mockAcctDataService.getAcctAuth()).thenReturn(new AuthPair(acctName, pw));
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).then(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                RestRequest.Connection mockConnection =
                        (RestRequest.Connection) invocation.getArguments()[0];
                when(mockConnection.getHeader(matches("(?i)etag"))).thenReturn(etag);
                return new JSONObject().put("saldo", 3).toString();
            }
        });
        Semaphore pauseSem = preparePause();
        bindService();
        pauseSem.release();
        verify(mockFetcher, timeout(2000).atLeast(2)).fetch(requestCaptor.capture());
        MockRestRequest mockRequest = requestCaptor.getAllValues().get(1).getRequest();
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(pw, mockRequest.getPassword());
        assertEquals("GET", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/saldo/", mockRequest.getUri());
        assertEquals(etag, mockRequest.getIfNoneMatch());
        int waitChange = mockRequest.getWaitChange();
        assertTrue(waitChange > 0);
        int connectTimeout = mockRequest.getConnectTimeout();
        assertTrue(connectTimeout == 0 || connectTimeout >= (waitChange + 1) * 1000);
    }

    public void testWaitForAcctAuth() throws Exception {
        when(mockAcctDataService.getAcctAuth()).thenReturn(null);
        bindService();
        Intent intent = broadcasts.poll(2, TimeUnit.SECONDS);
        assertEquals(SaldoService.UNKNOWN_CREDIT, intent.getIntExtra(
                SaldoService.EXTRA_CREDIT, 123));
        assertEquals(SaldoService.UNKNOWN_CREDIT, getService().getCredit());
        verify(mockFetcher, never()).fetch(any(RestRequest.Connection.class));

        when(mockAcctDataService.getAcctAuth()).thenReturn(new AuthPair("c/9121314", "xyz"));
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenReturn(
                new JSONObject().put("saldo", 4).toString());
        preparePause();
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(
                new Intent(AcctDataService.ACTION_ACCT_CHANGED));
        verify(mockFetcher, timeout(2000).atLeastOnce()).fetch(any(RestRequest.Connection.class));
    }
}
