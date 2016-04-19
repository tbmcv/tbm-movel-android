package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.linphone.core.LinphoneAddress.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AcctDataServiceTest
        extends BaseIntentServiceUnitTest<AcctDataServiceTest.TestingAcctDataService> {
    public static class TestingAcctDataService extends AcctDataService {
        @Override
        protected void onHandleIntent(Intent intent) {
            super.onHandleIntent(intent);
            setIntentHandled(getBaseContext(), intent);
        }
    }

    public AcctDataServiceTest() {
        super(TestingAcctDataService.class);
    }

    private MockJrcRequestBuilder.Fetcher fetcher;
    private ArgumentCaptor<Map<String, ?>> paramsCaptor;
    private AcctDataService.Pauser pauser;

    protected void setUp() throws Exception {
        fetcher = MockJrcRequestBuilder.mockDefaultClient();
        paramsCaptor = ArgumentCaptor.forClass((Class) Map.class);
        pauser = mock(AcctDataService.Pauser.class);
        AcctDataService.pauser = pauser;
        if (!LinphoneManager.isInstanciated()) {
            LinphoneManager.createAndStart(getContext());
        }
        super.setUp();
        clearVoipLines();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        AnswerPromise.cleanup();
        clearVoipLines();
    }

    protected void clearVoipLines() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.clearAuthInfos();
            lc.clearProxyConfigs();
        }
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

    private static void await(CountDownLatch latch) throws InterruptedException {
        latch.await(2, TimeUnit.SECONDS);
    }

    private static void await(AnswerPromise<?> promise) throws InterruptedException {
        await(promise.getCallLatch());
    }

    public void testGetCredit() throws Exception {
        String acctName = "c/5050505";
        String pw = "anything";
        setStoredAcct(acctName, pw);
        AnswerPromise<?> fetchPromise = new AnswerPromise<>();
        when(fetcher.fetch(any(Map.class))).then(fetchPromise);
        startService(new Intent(AcctDataService.ACTION_GET_CREDIT));
        await(fetchPromise);

        verify(fetcher).fetch(paramsCaptor.capture());
        Map<String, ?> params = paramsCaptor.getValue();
        assertEquals(acctName, params.get("username"));
        assertEquals(pw, params.get("password"));
        assertEquals("GET", params.get("method"));
        assertUriEquals("/idens/" + acctName + "/saldo/", params.get("uri"));
        assertNull(params.get("body"));
    }

    public void testGetCreditUnconfiguredActivitySwitch() throws Exception {
        clearPrefs();
        checkInitConfigActivitySwitch(new Intent(AcctDataService.ACTION_GET_CREDIT));
    }

    protected void checkInitConfigActivitySwitch(Intent intent) throws InterruptedException {
        startService(intent);
        assertActivityStarted(InitConfigActivity.class, 2, TimeUnit.SECONDS);
    }

    public void testGetCreditMisconfiguredActivitySwitch() throws Exception {
        setStoredAcct("c/9050509", "dafala");
        when(fetcher.fetch(any(Map.class))).thenThrow(new HttpError(401));
        checkInitConfigActivitySwitch(new Intent(AcctDataService.ACTION_GET_CREDIT));
    }

    public void testPwReset() throws Exception {
        String username = "c/9123456";
        String password = "123454321";
        SharedPreferences prefs = clearPrefs();

        AnswerPromise<?> fetchPromise = new AnswerPromise<>();
        when(fetcher.fetch(any(Map.class))).then(fetchPromise);
        startService(new Intent(AcctDataService.ACTION_RESET_PASSWORD)
                .putExtra(AcctDataService.EXTRA_ACCT_NAME, username)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        await(fetchPromise);

        verify(fetcher).fetch(paramsCaptor.capture());
        Map<String, ?> params = paramsCaptor.getValue();
        assertEquals(username, params.get("username"));
        assertEquals(password, params.get("password"));
        assertEquals("PUT", params.get("method"));
        assertUriEquals("/idens/" + username + "/pw", params.get("uri"));
        JSONObject body = (JSONObject) params.get("body");

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

        when(fetcher.fetch(any(Map.class))).thenReturn(null);
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_RESET_PASSWORD)
                .putExtra(AcctDataService.EXTRA_ACCT_NAME, "c/" + phoneNumber)
                .putExtra(AcctDataService.EXTRA_PASSWORD, oldPw));

        assertEquals("c/" + phoneNumber,
                prefs.getString(context.getString(R.string.tbm_setting_acctname),
                        "(NOTHING STORED)"));
        String storedPw = prefs.getString(context.getString(R.string.tbm_setting_password), null);
        assertNotNull(storedPw);
        assertNotSame(oldPw, storedPw);
    }

    public void testPwResetSendsBroadcast() throws Exception {
        when(fetcher.fetch(any(Map.class))).thenReturn(null);
        startServiceAndWaitForBroadcast(
                new Intent(AcctDataService.ACTION_RESET_PASSWORD)
                        .putExtra(AcctDataService.EXTRA_ACCT_NAME, "c/abcd")
                        .putExtra(AcctDataService.EXTRA_PASSWORD, "EFG"),
                AcctDataService.ACTION_PASSWORD_RESET);
    }

    public void testNewVoipLineRequest() throws Exception {
        String acctName = "c/5123456";
        String password = "blah";
        setStoredAcct(acctName, password);
        AnswerPromise<JSONObject> fetchPromise = new AnswerPromise<>();
        when(fetcher.fetch(any(Map.class)))
                .thenReturn(new JSONObject().put("lines", new JSONArray()))
                .then(fetchPromise);
        startService(new Intent(AcctDataService.ACTION_CONFIGURE_LINE));
        await(fetchPromise);
        verify(fetcher, times(2)).fetch(paramsCaptor.capture());

        Map<String, ?> params = paramsCaptor.getAllValues().get(0);
        assertEquals(acctName, params.get("username"));
        assertEquals(password, params.get("password"));
        assertEquals("GET", params.get("method"));
        assertUriEquals("/idens/" + acctName + "/lines/", params.get("uri"));

        params = paramsCaptor.getAllValues().get(1);
        assertEquals(acctName, params.get("username"));
        assertEquals(password, params.get("password"));
        assertEquals("POST", params.get("method"));
        assertUriEquals("/idens/" + acctName + "/lines/", params.get("uri"));
        assertTrue(params.get("body") instanceof JSONObject);
    }

    public void testNewVoipLineReconfigures() throws Exception {
        String acctName = "c/5123456";
        String password = "blah";
        String lineName = "tbm7777";
        String linePassword = "bleh";
        setStoredAcct(acctName, password);
        when(fetcher.fetch(any(Map.class)))
                .thenReturn(new JSONObject().put("lines", new JSONArray()))
                .thenReturn(new JSONObject().put("name", lineName).put("pw", linePassword));
        AnswerPromise<?> pausePromise = preparePause();
        CountDownLatch finished =
                sendServiceIntent(new Intent(AcctDataService.ACTION_CONFIGURE_LINE));
        assertTrue(verifyPauseMillis(pausePromise) >= 1000);

        pausePromise.setResult(null);
        await(finished);
        Intent resultIntent = getStartServiceTrap().getServiceStarted(
                AcctDataService.class, AcctDataService.ACTION_CONFIGURE_LINE);

        assertEquals(AcctDataService.ACTION_CONFIGURE_LINE, resultIntent.getAction());
        assertEquals(lineName, resultIntent.getStringExtra(AcctDataService.EXTRA_LINE_NAME));
        assertEquals(linePassword, resultIntent.getStringExtra(AcctDataService.EXTRA_PASSWORD));
    }

    public void testReconfigureVoipLineRequest() throws Exception {
        String acctName = "c/5110023";
        String password = "segredos";
        String lineName = "tbm2222";
        setStoredAcct(acctName, password);
        AnswerPromise<JSONObject> fetchPromise = new AnswerPromise<>();
        when(fetcher.fetch(any(Map.class)))
                .thenReturn(new JSONObject()
                        .put("lines", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", 1234)
                                        .put("name", lineName)
                                        .put("display", null))))
                .then(fetchPromise);
        startService(new Intent(AcctDataService.ACTION_CONFIGURE_LINE));
        await(fetchPromise);
        verify(fetcher, times(2)).fetch(paramsCaptor.capture());

        Map<String, ?> params = paramsCaptor.getAllValues().get(0);
        assertEquals(acctName, params.get("username"));
        assertEquals(password, params.get("password"));
        assertEquals("GET", params.get("method"));
        assertUriEquals("/idens/" + acctName + "/lines/", params.get("uri"));

        params = paramsCaptor.getAllValues().get(1);
        assertEquals(acctName, params.get("username"));
        assertEquals(password, params.get("password"));
        assertEquals("POST", params.get("method"));
        assertUriEquals("/idens/" + acctName + "/lines/" + lineName + "/pw", params.get("uri"));
        assertTrue(params.get("body") instanceof JSONObject);
    }

    public void testReconfigureVoipLineReconfigures() throws Exception {
        String acctName = "c/5110023";
        String password = "segredos";
        String lineName = "tbm2222";
        String linePassword = "caten";
        setStoredAcct(acctName, password);
        when(fetcher.fetch(any(Map.class)))
                .thenReturn(new JSONObject()
                        .put("lines", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", 1234)
                                        .put("name", lineName)
                                        .put("display", null))))
                .thenReturn(new JSONObject().put("pw", linePassword));
        AnswerPromise<?> pausePromise = preparePause();
        CountDownLatch finished =
                sendServiceIntent(new Intent(AcctDataService.ACTION_CONFIGURE_LINE));
        assertTrue(verifyPauseMillis(pausePromise) >= 1000);

        pausePromise.setResult(null);
        await(finished);
        Intent resultIntent = getStartServiceTrap().getServiceStarted(
                AcctDataService.class, AcctDataService.ACTION_CONFIGURE_LINE);

        assertEquals(AcctDataService.ACTION_CONFIGURE_LINE, resultIntent.getAction());
        assertEquals(lineName, resultIntent.getStringExtra(AcctDataService.EXTRA_LINE_NAME));
        assertEquals(linePassword, resultIntent.getStringExtra(AcctDataService.EXTRA_PASSWORD));
    }

    private <T> T getFromArrayOfOne(T... array) {
        assertEquals(1, array.length);
        return array[0];
    }

    private void assertEqualsOrNull(Object expected, Object actual) {
        if (actual != null) {
            assertEquals(expected, actual);
        }
    }

    void assertVoipLine(String lineName, String password) throws LinphoneCoreException {
        final String realm = getContext().getString(R.string.tbm_sip_realm);
        LinphoneCore lc = LinphoneManager.getLc();

        LinphoneAuthInfo auth = getFromArrayOfOne(lc.getAuthInfosList());
        assertEquals(lineName, auth.getUsername());
        assertEqualsOrNull(lineName, auth.getUserId());
        assertEquals(AcctDataService.createHa1(lineName, password, realm), auth.getHa1());
        assertEqualsOrNull(password, auth.getPassword());
        assertEquals(realm, auth.getDomain());
        assertEqualsOrNull(realm, auth.getRealm());

        LinphoneProxyConfig cfg = getFromArrayOfOne(lc.getProxyConfigList());
        assertEquals(realm, cfg.getDomain());
        LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(cfg.getProxy());
        assertEquals(realm, proxyAddr.getDomain());
        assertEquals(TransportType.LinphoneTransportTcp, proxyAddr.getTransport());
        assertTrue(cfg.registerEnabled());
    }

    void addVoipLine(String lineName, String password, String domain) throws LinphoneCoreException {
        LinphoneCore lc = LinphoneManager.getLc();
        Set<LinphoneAuthInfo> oldAuthInfos = new HashSet<>();
        Collections.addAll(oldAuthInfos, lc.getAuthInfosList());
        new LinphonePreferences.AccountBuilder(lc)
                .setUsername(lineName)
                .setPassword(password)
                .setDomain(domain)
                .setRealm(domain)
                .setProxy(domain)
                .setEnabled(true)
                .saveNewAccount();
        Set<LinphoneAuthInfo> authInfos = new HashSet<>();
        Collections.addAll(authInfos, lc.getAuthInfosList());
        authInfos.removeAll(oldAuthInfos);
        assertEquals(1, authInfos.size());
        LinphoneAuthInfo authInfo = authInfos.iterator().next();
        authInfo.setPassword(null);
        authInfo.setHa1(AcctDataService.createHa1(lineName, password, domain));
    }

    public void testConfigureNewVoipLine() throws Exception {
        String lineName = "tbm9999";
        String password = "pass";
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        assertVoipLine(lineName, password);
    }

    public void testReconfigureVoipLine() throws Exception {
        String lineName = "tbm5555";
        String password = "*****";
        addVoipLine("old", "values", "a.b.c");
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        assertVoipLine(lineName, password);
    }

    public void testConfigureLineUnconfiguredActivitySwitch() throws Exception {
        clearPrefs();
        checkInitConfigActivitySwitch(new Intent(AcctDataService.ACTION_CONFIGURE_LINE));
    }

    public void testConfigureLineMisconfiguredActivitySwitch() throws Exception {
        setStoredAcct("c/9152519", "dzemla");
        when(fetcher.fetch(any(Map.class))).thenThrow(new HttpError(401));
        checkInitConfigActivitySwitch(new Intent(AcctDataService.ACTION_CONFIGURE_LINE));
    }

    private static void assertUriEquals(URI expected, Object actual) {
        assertEquals(expected, URI.create("/").resolve((URI) actual));
    }

    private static void assertUriEquals(String expected, Object actual) {
        assertUriEquals(URI.create(expected), actual);
    }

    public void testEnsureLineNoExisting() throws Exception {
        checkEnsureLineTriggersConfigure();
    }

    private Intent checkEnsureLineTriggersConfigure() throws InterruptedException {
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_ENSURE_LINE));
        Intent resultIntent = getStartServiceTrap().getServiceStarted(
                AcctDataService.class, AcctDataService.ACTION_CONFIGURE_LINE);
        assertNotNull("CONFIGURE_LINE not triggered", resultIntent);
        return resultIntent;
    }

    private Intent callEnsureLineAndCheckApiCalls(String acctName, String acctPw, String lineName,
                                                  String storedLinePw, String apiLinePw)
            throws InterruptedException, JSONException, IOException, LinphoneCoreException {
        setStoredAcct(acctName, acctPw);
        addVoipLine(lineName, storedLinePw, "sip.tbmcv.com");
        when(fetcher.fetch(any(Map.class)))
                .thenReturn(new JSONObject().put("pw", apiLinePw));

        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_ENSURE_LINE));
        Intent resultIntent = getStartServiceTrap().getServiceStarted(
                AcctDataService.class, AcctDataService.ACTION_CONFIGURE_LINE);

        verify(fetcher).fetch(paramsCaptor.capture());

        Map<String, ?> params = paramsCaptor.getValue();
        assertEquals(acctName, params.get("username"));
        assertEquals(acctPw, params.get("password"));
        assertEquals("GET", params.get("method"));
        assertUriEquals("/idens/" + acctName + "/lines/" + lineName + "/pw", params.get("uri"));

        return resultIntent;
    }

    public void testEnsureLineKeepsCorrectCreds() throws Exception {
        String pw = "98w7efyu";
        assertNull("Reconfigured when credentials were the same as on the server",
                callEnsureLineAndCheckApiCalls("c/9119119", "joao*who?", "tbm8814", pw, pw));
    }

    public void testEnsureLineReconfiguresWhenCredsDifferent() throws Exception {
        assertNotNull("Didn't reconfigure when credentials were different from server's",
                callEnsureLineAndCheckApiCalls(
                        "c/9881889", "duvinha", "tbm8324",
                        "jk324h", "23kj4h"));
    }

    public void testEnsureLineUnconfiguredActivitySwitch() throws Exception {
        clearPrefs();
        checkInitConfigActivitySwitch(new Intent(AcctDataService.ACTION_ENSURE_LINE));
    }

    public void testCreateHa1() {
        assertEquals("9bc3f707a46d0bf0403b5dc2270d29b2",
                AcctDataService.createHa1("thisguy", "bruteforcethis", "here"));
    }
}
