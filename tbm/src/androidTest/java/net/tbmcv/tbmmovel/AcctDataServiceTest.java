package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.SharedPreferences;

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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AcctDataServiceTest extends BaseServiceUnitTest<AcctDataService> {
    public AcctDataServiceTest() {
        super(AcctDataService.class);
    }

    private AcctDataService.Pauser pauser;
    private TbmLinphoneConfigurator mockLinphoneConfigurator;
    private TbmApiService mockTbmApiService;
    private RestRequest.Fetcher mockFetcher;
    private ArgumentCaptor<MockRestRequest> requestCaptor;

    protected void setUp() throws Exception {
        super.setUp();
        mockLinphoneConfigurator = mock(TbmLinphoneConfigurator.class);
        TbmLinphoneConfigurator.instance = mockLinphoneConfigurator;
        mockTbmApiService = mock(TbmApiService.class);
        getStartServiceTrap().setBoundService(
                TbmApiService.class, new LocalServiceBinder<>(mockTbmApiService));
        mockFetcher = mock(RestRequest.Fetcher.class);
        MockRestRequest.mockTbmApi(mockTbmApiService, mockFetcher);
        pauser = mock(AcctDataService.Pauser.class);
        AcctDataService.pauser = pauser;
        requestCaptor = ArgumentCaptor.forClass(MockRestRequest.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        AnswerPromise.cleanup();
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

    protected AnswerPromise<?> preparePause() {
        AnswerPromise<?> pausePromise = new AnswerPromise<>();
        try {
            doAnswer(pausePromise).when(pauser).pause(anyInt(), any(TimeUnit.class));
        } catch (InterruptedException e) {
            throw new Error(e);
        }
        return pausePromise;
    }

    protected long verifyPauseMillis() {
        ArgumentCaptor<Long> duration = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unit = ArgumentCaptor.forClass(TimeUnit.class);
        try {
            verify(pauser).pause(duration.capture(), unit.capture());
        } catch (InterruptedException e) {
            throw new Error(e);
        }
        return unit.getValue().toMillis(duration.getValue());
    }

    protected long verifyPauseMillis(AnswerPromise<?> pausePromise) throws InterruptedException {
        await(pausePromise);
        return verifyPauseMillis();
    }

    public void testGetCredit() throws Exception {
        String acctName = "c/5050505";
        String pw = "anything";
        int saldo = 1234;
        setStoredAcct(acctName, pw);
        bindService();
        when(mockFetcher.fetch(any(RestRequest.class))).thenReturn(
                new JSONObject().put("saldo", saldo).toString());

        assertEquals(saldo, getService().getCredit());

        verify(mockFetcher).fetch(requestCaptor.capture());
        MockRestRequest mockRequest = requestCaptor.getValue();
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
        when(mockFetcher.fetch(any(RestRequest.class))).thenThrow(new HttpError(401));
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
        MockRestRequest mockRequest = requestCaptor.getValue();
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

    public void testNewVoipLineRequest() throws Exception {
        String acctName = "c/5123456";
        String password = "blah";
        setStoredAcct(acctName, password);
        when(mockFetcher.fetch(any(RestRequest.class)))
                .thenReturn(new JSONObject().put("lines", new JSONArray()).toString())
                .thenReturn(new JSONObject().put("name", "").put("pw", "").toString());
        bindService();
        getService().configureLine();
        verify(mockFetcher, times(2)).fetch(requestCaptor.capture());

        MockRestRequest mockRequest = requestCaptor.getAllValues().get(0);
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("GET", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/lines/", mockRequest.getUri());

        mockRequest = requestCaptor.getAllValues().get(1);
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("POST", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/lines/", mockRequest.getUri());
        assertNotNull(mockRequest.getBody());
        assertTrue(mockRequest.getBody() instanceof JSONObject);
    }

    public void testNewVoipLineReconfigures() throws Exception {
        String acctName = "c/5123456";
        String password = "blah";
        String lineName = "tbm7777";
        String linePassword = "bleh";
        setStoredAcct(acctName, password);
        when(mockFetcher.fetch(any(RestRequest.class)))
                .thenReturn(new JSONObject().put("lines", new JSONArray()).toString())
                .thenReturn(new JSONObject().put("name", lineName).put("pw", linePassword).toString());
        doNothing().when(pauser).pause(anyLong(), any(TimeUnit.class));
        bindService();
        getService().configureLine();

        assertTrue(verifyPauseMillis() >= 1000);
        verify(mockLinphoneConfigurator).configureLine(
                getContext().getString(R.string.tbm_sip_realm), lineName, linePassword);
    }

    public void testReconfigureVoipLineRequest() throws Exception {
        String acctName = "c/5110023";
        String password = "segredos";
        String lineName = "tbm2222";
        setStoredAcct(acctName, password);
        when(mockFetcher.fetch(any(RestRequest.class)))
                .thenReturn(new JSONObject()
                        .put("lines", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", 1234)
                                        .put("name", lineName)
                                        .put("display", null))).toString())
                .thenReturn(new JSONObject().put("pw", "whatever").toString());
        bindService();
        getService().configureLine();
        verify(mockFetcher, times(2)).fetch(requestCaptor.capture());

        MockRestRequest mockRequest = requestCaptor.getAllValues().get(0);
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("GET", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/lines/", mockRequest.getUri());

        mockRequest = requestCaptor.getAllValues().get(1);
        assertEquals(acctName, mockRequest.getUsername());
        assertEquals(password, mockRequest.getPassword());
        assertEquals("POST", mockRequest.getMethod());
        assertUriEquals("/idens/" + acctName + "/lines/" + lineName + "/pw", mockRequest.getUri());
        assertNotNull(mockRequest.getBody());
        assertTrue(mockRequest.getBody() instanceof JSONObject);
    }

    public void testReconfigureVoipLineReconfigures() throws Exception {
        String acctName = "c/5110023";
        String password = "segredos";
        String lineName = "tbm2222";
        String linePassword = "caten";
        setStoredAcct(acctName, password);
        when(mockFetcher.fetch(any(RestRequest.class)))
                .thenReturn(new JSONObject()
                        .put("lines", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", 1234)
                                        .put("name", lineName)
                                        .put("display", null))).toString())
                .thenReturn(new JSONObject().put("pw", linePassword).toString());
        bindService();
        getService().configureLine();
        assertTrue(verifyPauseMillis() >= 1000);
        verify(mockLinphoneConfigurator).configureLine(
                getContext().getString(R.string.tbm_sip_realm), lineName, linePassword);
    }

    public void testConfigureLineUnconfiguredActivitySwitch() throws Exception {
        clearPrefs();
        bindService();
        getService().configureLine();
        checkInitConfigActivitySwitch();
    }

    public void testConfigureLineMisconfiguredActivitySwitch() throws Exception {
        setStoredAcct("c/9152519", "dzemla");
        when(mockFetcher.fetch(any(RestRequest.class))).thenThrow(new HttpError(401));
        bindService();
        try {
            getService().configureLine();
        } catch (HttpError e) {
            /* fall through */
        }
        checkInitConfigActivitySwitch();
    }

    private static void assertUriEquals(URI expected, Object actual) {
        assertEquals(expected, URI.create("/").resolve((URI) actual));
    }

    private static void assertUriEquals(String expected, Object actual) {
        assertUriEquals(URI.create(expected), actual);
    }

    private void setupEnsureLine(final String acctName, String acctPw, final String lineName,
                                 String storedLinePw, final String apiLinePw)
            throws InterruptedException, JSONException, IOException, LinphoneCoreException {
        setStoredAcct(acctName, acctPw);
        AuthPair storedAuth = null;
        if (storedLinePw != null) {
            storedAuth = new AuthPair(lineName, AcctDataService.createHa1(
                    lineName, storedLinePw, getContext().getString(R.string.tbm_sip_realm)));
        }
        when(mockLinphoneConfigurator.getLineConfig()).thenReturn(storedAuth);
        when(mockFetcher.fetch(any(RestRequest.class))).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                MockRestRequest request = (MockRestRequest) invocation.getArguments()[0];
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

    public void testEnsureLineKeepsCorrectCreds() throws Exception {
        String pw = "98w7efyu";
        setupEnsureLine("c/9119119", "joao*who?", "tbm8814", pw, pw);
        bindService();
        getService().ensureLine();
        verify(mockLinphoneConfigurator, never()).clearLineConfig();
        verify(mockLinphoneConfigurator, never()).configureLine(anyString(), anyString(), anyString());
    }

    public void testEnsureLineReconfiguresWhenCredsDifferent() throws Exception {
        String lineName = "tbm8324";
        String oldStoredPw = "jk324h";
        String currentPw = "23kj4h";
        setupEnsureLine("c/9881889", "duvinha", lineName, oldStoredPw, currentPw);
        bindService();
        getService().ensureLine();
        ArgumentCaptor<String> newPwCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLinphoneConfigurator).configureLine(
                eq(getContext().getString(R.string.tbm_sip_realm)), eq(lineName), newPwCaptor.capture());
        assertNotSame(oldStoredPw, newPwCaptor.getValue());
        assertNotSame(currentPw, newPwCaptor.getValue());
    }

    public void testEnsureLineReconfiguresWhenUnconfigured() throws Exception {
        String lineName = "tbm8324";
        String currentPw = "23kj4h";
        setupEnsureLine("c/9881889", "duvinha", lineName, null, currentPw);
        bindService();
        getService().ensureLine();
        ArgumentCaptor<String> newPwCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLinphoneConfigurator).configureLine(
                eq(getContext().getString(R.string.tbm_sip_realm)), eq(lineName), newPwCaptor.capture());
        assertNotSame(currentPw, newPwCaptor.getValue());
    }

    public void testEnsureLineUnconfiguredActivitySwitch() throws Exception {
        clearPrefs();
        bindService();
        getService().ensureLine();
        checkInitConfigActivitySwitch();
    }

    public void testCreateHa1() {
        assertEquals("9bc3f707a46d0bf0403b5dc2270d29b2",
                AcctDataService.createHa1("thisguy", "bruteforcethis", "here"));
    }
}
