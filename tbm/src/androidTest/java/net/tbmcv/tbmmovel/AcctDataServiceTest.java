package net.tbmcv.tbmmovel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.core.LinphoneCoreException;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AcctDataServiceTest extends BaseServiceUnitTest<AcctDataServiceTest.TestingAcctDataService> {
    public AcctDataServiceTest() {
        super(TestingAcctDataService.class);
    }

    public static class TestingAcctDataService extends AcctDataService {
        synchronized void waitIdle(long millis) throws TimeoutException, InterruptedException {
            long end = System.currentTimeMillis() + millis;
            while (state != NOTHING_REQUESTED && state != SHUTTING_DOWN) {
                long remaining = end - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new TimeoutException();
                }
                wait(remaining);
            }
        }

        void waitIdle() throws TimeoutException, InterruptedException {
            waitIdle(2000);
        }
    }

    private TbmLinphoneConfigurator mockLinphoneConfigurator;
    private TbmApiService mockTbmApiService;
    private RestRequest.Fetcher mockFetcher;
    private ArgumentCaptor<MockRestRequest.Connection> requestCaptor;
    private TbmApiService.Binder tbmApiBinder;
    private LocalBroadcastReceiverManager broadcastReceivers;
    private ConnectivityManager connectivityManager;
    private NetworkInfo networkInfo;

    protected void setUp() throws Exception {
        super.setUp();
        mockLinphoneConfigurator = mock(TbmLinphoneConfigurator.class);
        TbmLinphoneConfigurator.instance = mockLinphoneConfigurator;
        mockTbmApiService = mock(TbmApiService.class);
        tbmApiBinder = new TbmApiService.Binder(mockTbmApiService);
        getStartServiceTrap().setBoundService(TbmApiService.class, tbmApiBinder);

        mockFetcher = mock(RestRequest.Fetcher.class);
        MockRestRequest.mockTbmApiRequests(mockTbmApiService, mockFetcher);

        AcctDataService.pauser = mockPauser;
        requestCaptor = ArgumentCaptor.forClass(MockRestRequest.Connection.class);
        broadcastReceivers = new LocalBroadcastReceiverManager(getContext());

        connectivityManager = mock(ConnectivityManager.class);
        when(getContext().getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(connectivityManager);
        networkInfo = mock(NetworkInfo.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
        when(networkInfo.isConnected()).thenReturn(true);
    }

    @Override
    protected void bindService() {
        super.bindService();
        tbmApiBinder.setReady();
    }

    @Override
    protected void tearDown() throws Exception {
        broadcastReceivers.unregisterAll();
        super.tearDown();
        AcctDataService.pauser = new RealPauser();
        TbmLinphoneConfigurator.instance = new TbmLinphoneConfigurator();
    }

    protected void setStoredAcct(String acctName, String pw) {
        Context context = getContext();
        context.getSharedPreferences(context.getString(R.string.tbm_settings_key), Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putString(context.getString(R.string.tbm_setting_acctname), acctName)
                .putString(context.getString(R.string.tbm_setting_password), pw)
                .commit();
    }

    protected SharedPreferences clearPrefs() {
        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.tbm_settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        return prefs;
    }

    public void testGetCredit() throws Exception {
        String acctName = "c/5050505";
        String pw = "anything";
        int saldo = 1234;
        setStoredAcct(acctName, pw);
        bindService();
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenReturn(
                new JSONObject().put("saldo", saldo).toString());

        assertEquals(saldo, getService().getCredit());

        verify(mockFetcher).fetch(requestCaptor.capture());
        MockRestRequest mockRequest = requestCaptor.getValue().getRequest();
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(pw, mockRequest.getPassword());
        assertEquals("GET", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/saldo/", mockRequest.getUri());
        assertNull(mockRequest.getBody());
    }

    public void testGetCreditUnconfiguredActivitySwitch() throws Exception {
        clearPrefs();
        bindService();
        try {
            getService().getCredit();
        } catch (HttpError e) {
            /* fall through */
        }
        checkInitConfigActivitySwitch();
    }

    protected void checkInitConfigActivitySwitch() throws Exception {
        new AssertWaiter() {
            @Override
            protected void test() throws Exception {
                assertActivityStarted(InitConfigActivity.class);
            }
        }.await();
    }

    public void testGetCreditMisconfiguredActivitySwitch() throws Exception {
        setStoredAcct("c/9050509", "dafala");
        bindService();
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenThrow(new HttpError(401));
        try {
            getService().getCredit();
        } catch (HttpError e) {
            /* fall through */
        }
        checkInitConfigActivitySwitch();
    }

    public void testPwReset() throws Exception {
        String username = "c/9123456";
        String password = "123454321";
        SharedPreferences prefs = clearPrefs();
        bindService();

        getService().resetPassword(username, password);

        verify(mockFetcher).fetch(requestCaptor.capture());
        MockRestRequest mockRequest = requestCaptor.getValue().getRequest();
        assertEquals(username, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("PUT", mockRequest.getMethod());
        assertUriEquals("/idens/" + username + "/pw", mockRequest.getUri());
        JSONObject body = (JSONObject) mockRequest.getBody();

        String newPw = body.getString("value");
        assertTrue(newPw.length() >= 4);
        assertTrue(newPw.length() < 30);
        Context context = getContext();
        assertEquals(username,
                prefs.getString(context.getString(R.string.tbm_setting_acctname),
                        "(NOTHING STORED)"));
        assertEquals(newPw,
                prefs.getString(context.getString(R.string.tbm_setting_password),
                        "(NOTHING STORED)"));
    }

    public void testPwResetChanges() throws Exception {
        String phoneNumber = "9999999";
        String oldPw = "gg";
        Context context = getContext();
        SharedPreferences prefs = clearPrefs();
        bindService();

        getService().resetPassword("c/" + phoneNumber, oldPw);

        assertEquals("c/" + phoneNumber,
                prefs.getString(context.getString(R.string.tbm_setting_acctname),
                        "(NOTHING STORED)"));
        String storedPw = prefs.getString(context.getString(R.string.tbm_setting_password), null);
        assertNotNull(storedPw);
        assertNotSame(oldPw, storedPw);
    }

    public void testPwResetBroadcastsUpdate() throws Exception {
        bindService();

        BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        broadcastReceivers.registerReceiver(
                mockReceiver, new IntentFilter(AcctDataService.ACTION_ACCT_CHANGED));
        getService().resetPassword("c/9123210", "blah");
        verify(mockReceiver, timeout(2000)).onReceive(
                any(Context.class), intentCaptor.capture());
        assertEquals(AcctDataService.ACTION_ACCT_CHANGED, intentCaptor.getValue().getAction());
    }

    public void testResetLineNewLineRequest() throws Exception {
        String acctName = "c/5123456";
        String password = "blah";
        setStoredAcct(acctName, password);
        when(mockFetcher.fetch(any(RestRequest.Connection.class)))
                .thenReturn(new JSONObject().put("lines", new JSONArray()).toString())
                .thenReturn(new JSONObject().put("name", "").put("pw", "").toString());
        bindService();
        getService().resetLine();
        verify(mockFetcher, timeout(2000).times(2)).fetch(requestCaptor.capture());

        MockRestRequest mockRequest = requestCaptor.getAllValues().get(0).getRequest();
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("GET", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/lines/", mockRequest.getUri());

        mockRequest = requestCaptor.getAllValues().get(1).getRequest();
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("POST", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/lines/", mockRequest.getUri());
        assertNotNull(mockRequest.getBody());
        assertTrue(mockRequest.getBody() instanceof JSONObject);
    }

    public void testResetLineConfiguresNewLine() throws Exception {
        String acctName = "c/5123456";
        String password = "blah";
        String lineName = "tbm7777";
        String linePassword = "bleh";
        setStoredAcct(acctName, password);
        when(mockFetcher.fetch(any(RestRequest.Connection.class)))
                .thenReturn(new JSONObject().put("lines", new JSONArray()).toString())
                .thenReturn(new JSONObject().put("name", lineName).put("pw", linePassword).toString());
        doNothing().when(mockPauser).pause(anyLong(), any(TimeUnit.class));
        bindService();
        getService().resetLine();

        assertTrue(verifyPauseMillis(timeout(2000)) >= 1000);
        verify(mockLinphoneConfigurator, timeout(1000)).configureLine(
                getContext().getString(R.string.tbm_sip_realm), lineName, linePassword);
    }

    public void testResetLineRequest() throws Exception {
        String acctName = "c/5110023";
        String password = "segredos";
        String lineName = "tbm2222";
        setStoredAcct(acctName, password);
        when(mockFetcher.fetch(any(RestRequest.Connection.class)))
                .thenReturn(new JSONObject()
                        .put("lines", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", 1234)
                                        .put("name", lineName)
                                        .put("display", null))).toString())
                .thenReturn(new JSONObject().put("pw", "whatever").toString());
        bindService();
        getService().resetLine();
        verify(mockFetcher, timeout(2000).times(2)).fetch(requestCaptor.capture());

        MockRestRequest mockRequest = requestCaptor.getAllValues().get(0).getRequest();
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("GET", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/lines/", mockRequest.getUri());

        mockRequest = requestCaptor.getAllValues().get(1).getRequest();
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("POST", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/lines/" + lineName + "/pw", mockRequest.getUri());
        assertNotNull(mockRequest.getBody());
        assertTrue(mockRequest.getBody() instanceof JSONObject);
    }

    public void testResetLineReconfigures() throws Exception {
        String acctName = "c/5110023";
        String password = "segredos";
        String lineName = "tbm2222";
        String linePassword = "caten";
        setStoredAcct(acctName, password);
        when(mockFetcher.fetch(any(RestRequest.Connection.class)))
                .thenReturn(new JSONObject()
                        .put("lines", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", 1234)
                                        .put("name", lineName)
                                        .put("display", null))).toString())
                .thenReturn(new JSONObject().put("pw", linePassword).toString());
        bindService();
        getService().resetLine();
        assertTrue(verifyPauseMillis(timeout(2000)) >= 1000);
        verify(mockLinphoneConfigurator, timeout(1000)).configureLine(
                getContext().getString(R.string.tbm_sip_realm), lineName, linePassword);
    }

    public void testResetLineUnconfiguredActivitySwitch() throws Exception {
        clearPrefs();
        bindService();
        getService().resetLine();
        checkInitConfigActivitySwitch();
    }

    public void testResetLineMisconfiguredActivitySwitch() throws Exception {
        setStoredAcct("c/9152519", "dzemla");
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenThrow(new HttpError(401));
        bindService();
        getService().resetLine();
        checkInitConfigActivitySwitch();
    }

    private void setupCheckLine(final String acctName, String acctPw, final String lineName,
                                String storedLinePw, final String apiLinePw)
            throws InterruptedException, JSONException, IOException, LinphoneCoreException {
        setStoredAcct(acctName, acctPw);
        AuthPair storedAuth = null;
        if (storedLinePw != null) {
            storedAuth = new AuthPair(lineName, AcctDataService.createHa1(
                    lineName, storedLinePw, getContext().getString(R.string.tbm_sip_realm)));
        }
        when(mockLinphoneConfigurator.getLineConfig()).thenReturn(storedAuth);
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                MockRestRequest request =
                        ((MockRestRequest.Connection) invocation.getArguments()[0]).getRequest();
                String method = request.getMethod();
                URI uri = URI.create("/").resolve(request.getUri());
                URI linesUri = URI.create("/idens/" + acctName + "/lines/");
                URI lineUri = linesUri.resolve(lineName + "/pw");
                if ("GET".equals(method) && linesUri.equals(uri)) {
                    return new JSONObject().put("lines", new JSONArray()
                            .put(new JSONObject().put("name", lineName))).toString();
                } else if ("GET".equals(method) && lineUri.equals(uri)) {
                    return new JSONObject().put("pw", apiLinePw).toString();
                } else if ("POST".equals(method) && lineUri.equals(uri)) {
                    return new JSONObject().put("pw", "<CHANGED>").toString();
                } else {
                    fail("Unexpected request: " + method + " " + uri);
                    return null;
                }
            }
        });
    }

    public void testCheckLineKeepsCorrectCreds() throws Exception {
        String pw = "98w7efyu";
        setupCheckLine("c/9119119", "joao*who?", "tbm8814", pw, pw);
        bindService();
        getService().checkLine();
        getService().waitIdle();
        verify(mockLinphoneConfigurator, never()).clearLineConfig();
        verify(mockLinphoneConfigurator, never()).configureLine(anyString(), anyString(), anyString());
    }

    public void testCheckLineReconfiguresWhenCredsDifferent() throws Exception {
        String lineName = "tbm8324";
        String oldStoredPw = "jk324h";
        String currentPw = "23kj4h";
        setupCheckLine("c/9881889", "duvinha", lineName, oldStoredPw, currentPw);
        bindService();
        getService().checkLine();
        ArgumentCaptor<String> newPwCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLinphoneConfigurator, timeout(2000)).configureLine(
                eq(getContext().getString(R.string.tbm_sip_realm)), eq(lineName), newPwCaptor.capture());
        assertNotSame(oldStoredPw, newPwCaptor.getValue());
        assertNotSame(currentPw, newPwCaptor.getValue());
    }

    public void testCheckLineReconfiguresWhenUnconfigured() throws Exception {
        String lineName = "tbm8324";
        String currentPw = "23kj4h";
        setupCheckLine("c/9881889", "duvinha", lineName, null, currentPw);
        bindService();
        getService().checkLine();
        ArgumentCaptor<String> newPwCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLinphoneConfigurator, timeout(2000)).configureLine(
                eq(getContext().getString(R.string.tbm_sip_realm)), eq(lineName), newPwCaptor.capture());
        assertNotSame(currentPw, newPwCaptor.getValue());
    }

    public void testCheckLineUnconfiguredActivitySwitch() throws Exception {
        clearPrefs();
        bindService();
        getService().checkLine();
        checkInitConfigActivitySwitch();
    }

    public void testCheckLineWaitsForNetwork() throws Exception {
        String lineName = "tbm8324";
        String currentPw = "23kj4h";
        setupCheckLine("c/9881889", "duvinha", lineName, null, currentPw);
        when(networkInfo.isConnected()).thenReturn(false);
        bindService();

        getService().checkLine();
        verify(mockFetcher, after(1000).never()).fetch(any(RestRequest.Connection.class));

        BroadcastReceiver networkReceiver = getNetworkReceiver();
        when(networkInfo.isConnected()).thenReturn(true);
        networkReceiver.onReceive(
                getContext(), new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
        verify(mockFetcher, timeout(2000).atLeastOnce()).fetch(any(RestRequest.Connection.class));
    }

    public void testCreateHa1() {
        assertEquals("9bc3f707a46d0bf0403b5dc2270d29b2",
                AcctDataService.createHa1("thisguy", "bruteforcethis", "here"));
    }

    public void testCreateRequest() throws Exception {
        final AuthPair auth = new AuthPair("c/9009009", "pswd");
        final String result = "hello";
        bindService();
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenReturn(result);
        RestRequest request = getService().createRequest(auth);
        assertEquals(result, request.fetch());
        verify(mockFetcher).fetch(requestCaptor.capture());
        assertEquals(request, requestCaptor.getValue().getRequest());
    }

    public void testCreateRequest401Resets() throws Exception {
        final AuthPair auth = new AuthPair("c/9009009", "pswd");
        setStoredAcct(auth.name, auth.password);
        bindService();

        BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        broadcastReceivers.registerReceiver(
                mockReceiver, new IntentFilter(AcctDataService.ACTION_ACCT_CHANGED));
        when(mockFetcher.fetch(any(RestRequest.Connection.class))).thenThrow(new HttpError(401));

        RestRequest request = getService().createRequest(auth);
        try {
            request.fetch();
            fail("401 not thrown");
        } catch (HttpError e) {
            assertEquals(401, e.getResponseCode());
        }

        Context context = getContext();
        assertNull(clearPrefs().getString(context.getString(R.string.tbm_setting_acctname), null));
        assertNull(clearPrefs().getString(context.getString(R.string.tbm_setting_password), null));

        assertActivityStarted(InitConfigActivity.class);

        verify(mockReceiver, timeout(2000)).onReceive(
                any(Context.class), intentCaptor.capture());
        assertEquals(AcctDataService.ACTION_ACCT_CHANGED, intentCaptor.getValue().getAction());
    }
}
