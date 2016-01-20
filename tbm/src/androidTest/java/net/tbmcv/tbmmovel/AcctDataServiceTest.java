package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.linphone.core.LinphoneAddress.*;
import static org.mockito.Matchers.any;
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

    protected void setUp() throws Exception {
        fetcher = MockJrcRequestBuilder.mockDefaultClient();
        paramsCaptor = ArgumentCaptor.forClass((Class) Map.class);
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

    public void testGetCredit() throws Exception {
        String acctName = "c/5050505";
        String pw = "anything";
        setStoredAcct(acctName, pw);
        AnswerPromise<?> fetchPromise = new AnswerPromise<>();
        when(fetcher.fetch(any(Map.class))).then(fetchPromise);
        startService(new Intent(AcctDataService.ACTION_GET_CREDIT));
        fetchPromise.getCallLatch().await(2, TimeUnit.SECONDS);

        verify(fetcher).fetch(paramsCaptor.capture());
        Map<String, ?> params = paramsCaptor.getValue();
        assertEquals(acctName, params.get("username"));
        assertEquals(pw, params.get("password"));
        assertEquals("GET", params.get("method"));
        assertUriEquals("/idens/" + acctName + "/saldo/", params.get("uri"));
        assertNull(params.get("body"));
    }

    public void testGetCreditUnconfiguredActivitySwitch() throws Exception {
        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.tbm_settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        startService(new Intent(AcctDataService.ACTION_GET_CREDIT));
        assertActivityStarted(InitConfigActivity.class, 2, TimeUnit.SECONDS);
    }

    public void testPwReset() throws Exception {
        String username = "c/9123456";
        String password = "123454321";
        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.tbm_settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        AnswerPromise<?> fetchPromise = new AnswerPromise<>();
        when(fetcher.fetch(any(Map.class))).then(fetchPromise);
        startService(new Intent(AcctDataService.ACTION_RESET_PASSWORD)
                .putExtra(AcctDataService.EXTRA_ACCT_NAME, username)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        fetchPromise.getCallLatch().await(2, TimeUnit.SECONDS);

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
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.tbm_settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

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
        String lineName = "tbm7777";
        String linePassword = "bleh";
        setStoredAcct(acctName, password);
        when(fetcher.fetch(any(Map.class)))
                .thenReturn(new JSONObject().put("lines", new JSONArray()))
                .thenReturn(new JSONObject().put("name", lineName).put("pw", linePassword));

        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_CONFIGURE_LINE));
        Intent resultIntent = getStartServiceTrap().getServiceStarted(
                AcctDataService.class, AcctDataService.ACTION_CONFIGURE_LINE);

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

        assertEquals(AcctDataService.ACTION_CONFIGURE_LINE, resultIntent.getAction());
        assertEquals(lineName, resultIntent.getStringExtra(AcctDataService.EXTRA_LINE_NAME));
        assertEquals(linePassword, resultIntent.getStringExtra(AcctDataService.EXTRA_PASSWORD));
    }

    public void testReconfigureVoipLineRequest() throws Exception {
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

        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_CONFIGURE_LINE));
        Intent resultIntent = getStartServiceTrap().getServiceStarted(
                AcctDataService.class, AcctDataService.ACTION_CONFIGURE_LINE);

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

        assertEquals(AcctDataService.ACTION_CONFIGURE_LINE, resultIntent.getAction());
        assertEquals(lineName, resultIntent.getStringExtra(AcctDataService.EXTRA_LINE_NAME));
        assertEquals(linePassword, resultIntent.getStringExtra(AcctDataService.EXTRA_PASSWORD));
    }

    private <T> T getFromArrayOfOne(T... array) {
        assertEquals(1, array.length);
        return array[0];
    }

    void checkVoipLine(String lineName, String password) throws LinphoneCoreException {
        final String realm = getContext().getString(R.string.tbm_sip_realm);
        LinphoneCore lc = LinphoneManager.getLc();

        LinphoneAuthInfo auth = getFromArrayOfOne(lc.getAuthInfosList());
        assertEquals(lineName, auth.getUsername());
        String userId = auth.getUserId();
        if (userId != null) {
            assertEquals(lineName, userId);
        }
        assertEquals(password, auth.getPassword());
        assertEquals(realm, auth.getDomain());
        String realmValue = auth.getRealm();
        if (realmValue != null) {
            assertEquals(realm, realmValue);
        }

        LinphoneProxyConfig cfg = getFromArrayOfOne(lc.getProxyConfigList());
        assertEquals(realm, cfg.getDomain());
        LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(cfg.getProxy());
        assertEquals(realm, proxyAddr.getDomain());
        assertEquals(TransportType.LinphoneTransportTcp, proxyAddr.getTransport());
        assertTrue(cfg.registerEnabled());
    }

    void addVoipLine(String lineName, String password, String domain) throws LinphoneCoreException {
        LinphoneCore lc = LinphoneManager.getLc();
        new LinphonePreferences.AccountBuilder(lc)
                .setUsername(lineName)
                .setPassword(password)
                .setDomain(domain)
                .setRealm(domain)
                .setProxy(domain)
                .setEnabled(true)
                .saveNewAccount();
    }

    public void testConfigureNewVoipLine() throws Exception {
        String lineName = "tbm9999";
        String password = "pass";
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        checkVoipLine(lineName, password);
    }

    public void testReconfigureVoipLine() throws Exception {
        String lineName = "tbm5555";
        String password = "*****";
        addVoipLine("old", "values", "a.b.c");
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        checkVoipLine(lineName, password);
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
}
