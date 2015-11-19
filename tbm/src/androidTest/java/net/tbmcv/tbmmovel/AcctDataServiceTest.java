package net.tbmcv.tbmmovel;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.R;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AcctDataServiceTest
        extends BaseIntentServiceUnitTest<AcctDataServiceTest.TestingAcctDataService> {
    private static final String LINE_DISPLAY_NAME = "TBM Móvel (auto)";

    /* Can't be static in Android 2.3 */
    private final String[] WRONG_LINE_NAMES = {"TBM", "line1", ""};

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
    private TestingContentProvider contentProvider;

    protected void setUp() throws Exception {
        fetcher = MockJrcRequestBuilder.mockDefaultClient();
        paramsCaptor = ArgumentCaptor.forClass((Class) Map.class);
        super.setUp();
        /*
        contentProvider = new TestingContentProvider(SipProfile.ACCOUNT_URI.getAuthority())
                .addTable(SipProfile.ACCOUNTS_TABLE_NAME, SipProfile.FIELD_ID,
                        SipProfile.FIELD_DISPLAY_NAME,
                        SipProfile.FIELD_ACTIVE,
                        SipProfile.FIELD_ACC_ID,
                        SipProfile.FIELD_REG_URI,
                        SipProfile.FIELD_REALM,
                        SipProfile.FIELD_SCHEME,
                        SipProfile.FIELD_USERNAME,
                        SipProfile.FIELD_DATATYPE,
                        SipProfile.FIELD_DATA)
                .addTable(SipProfile.ACCOUNTS_STATUS_TABLE_NAME, SipProfileState.ACCOUNT_ID,
                        SipProfileState.FULL_PROJECTION);
        contentProvider.attachInfo(getContext(), null);
        contentProvider.onCreate();
        getContentResolver().addProvider(SipProfile.ACCOUNT_URI.getAuthority(), contentProvider);
        */
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        AnswerPromise.cleanup();
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

    public void testPwResetRequestSent() throws Exception {
        String username = "c/9123456";
        String password = "123454321";
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
        assertEquals("POST", params.get("method"));
        assertUriEquals("/idens/" + username + "/pw", params.get("uri"));
        JSONObject body = (JSONObject) params.get("body");
        assertEquals("base64", body.get("reset"));
        int size = body.getInt("size");
        assertTrue(size >= 4);
        assertTrue(size < 30);
    }

    public void testLoginResetSuccessSavesCreds() throws Exception {
        String phoneNumber = "9999999";
        String newPw = "gg";
        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.tbm_settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        when(fetcher.fetch(any(Map.class))).thenReturn(new JSONObject().put("pw", newPw));
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_RESET_PASSWORD)
                .putExtra(AcctDataService.EXTRA_ACCT_NAME, "c/" + phoneNumber)
                .putExtra(AcctDataService.EXTRA_PASSWORD, "g2g"));

        assertEquals("c/" + phoneNumber,
                prefs.getString(context.getString(R.string.tbm_setting_acctname), "(NOTHING STORED)"));
        assertEquals(newPw,
                prefs.getString(context.getString(R.string.tbm_setting_password), "(NOTHING STORED)"));
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

        Intent resultIntent = startServiceAndWaitForBroadcast(
                new Intent(AcctDataService.ACTION_CONFIGURE_LINE),
                AcctDataService.ACTION_CONFIGURE_LINE);

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

        Intent resultIntent = startServiceAndWaitForBroadcast(
                new Intent(AcctDataService.ACTION_CONFIGURE_LINE),
                AcctDataService.ACTION_CONFIGURE_LINE);

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

    void checkVoipLine(String lineName, String password) {
        final String realm = getContext().getString(R.string.tbm_sip_realm);
        final String displayName = getContext().getString(R.string.tbm_csipsimple_display_name);
        /*
        Cursor cursor = contentProvider.query(
                SipProfile.ACCOUNT_URI,
                new String[]{
                        SipProfile.FIELD_ACTIVE,
                        SipProfile.FIELD_ACC_ID,
                        SipProfile.FIELD_REG_URI,
                        SipProfile.FIELD_REALM,
                        SipProfile.FIELD_SCHEME,
                        SipProfile.FIELD_USERNAME,
                        SipProfile.FIELD_DATATYPE,
                        SipProfile.FIELD_DATA,
                },
                SipProfile.FIELD_DISPLAY_NAME + " = ?", new String[]{displayName},
                null);
        try {
            assertTrue("No CSipSimple line with display name", cursor.moveToFirst());
            int i = 0;
            assertEquals(1, cursor.getInt(i++));
            assertEquals("sip:" + lineName + "@" + realm, cursor.getString(i++));
            assertEquals("sip:" + realm, cursor.getString(i++));
            assertEquals(realm, cursor.getString(i++));
            assertEquals(SipProfile.CRED_SCHEME_DIGEST, cursor.getString(i++));
            assertEquals(lineName, cursor.getString(i++));
            assertEquals(SipProfile.CRED_DATA_PLAIN_PASSWD, cursor.getInt(i++));
            assertEquals(password, cursor.getString(i++));
            assertFalse("More than one CSipSimple line with display name", cursor.moveToNext());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        */
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
        /*
        ContentValues oldValues = new ContentValues();
        oldValues.put(SipProfile.FIELD_DISPLAY_NAME, LINE_DISPLAY_NAME);
        contentProvider.insert(SipProfile.ACCOUNT_URI, oldValues);
        */
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        checkVoipLine(lineName, password);
    }

    void checkOtherVoipLinesLeft(String lineName, String password) throws InterruptedException {
        String otherDisplayName = "leave me alone!";
        String otherAccId = "sip:leave@me.alone.net";
        /*
        ContentValues oldValues = new ContentValues();
        oldValues.put(SipProfile.FIELD_DISPLAY_NAME, otherDisplayName);
        oldValues.put(SipProfile.FIELD_ACC_ID, otherAccId);
        Uri otherUri = contentProvider.insert(SipProfile.ACCOUNT_URI, oldValues);
        */
        sendServiceIntentAndWait(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        /*
        Cursor cursor = contentProvider.query(otherUri,
                new String[]{
                        SipProfile.FIELD_DISPLAY_NAME,
                        SipProfile.FIELD_ACC_ID,
                }, null, null, null);
        try {
            assertTrue("Other CSipSimple line gone", cursor.moveToFirst());
            int i = 0;
            assertEquals(otherDisplayName, cursor.getString(i++));
            assertEquals(otherAccId, cursor.getString(i++));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        */
    }

    public void testNewLineLeavesOtherVoipLines() throws Exception {
        checkOtherVoipLinesLeft("tbm5432", "lalala");
    }

    public void testReconfigureLeavesOtherVoipLines() throws Exception {
        ContentValues oldValues = new ContentValues();
        /*
        oldValues.put(SipProfile.FIELD_DISPLAY_NAME, LINE_DISPLAY_NAME);
        contentProvider.insert(SipProfile.ACCOUNT_URI, oldValues);
        */
        checkOtherVoipLinesLeft("tbm1111", "abcdefg");
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

    public void testEnsureLineExistingDifferentName() throws Exception {
        for (String name : WRONG_LINE_NAMES) {
            int i = name.hashCode();
            //insertProfileAndStatus(i, name, "tbm" + i, name, true, 3600);
            checkEnsureLineTriggersConfigure();
        }
    }

    private Intent checkEnsureLineTriggersConfigure() throws InterruptedException {
        return startServiceAndWaitForBroadcast(
                new Intent(AcctDataService.ACTION_ENSURE_LINE),
                AcctDataService.ACTION_CONFIGURE_LINE);
    }

    /*
    private void insertProfileAndStatus(int id, String displayName, String lineName,
                                        String password, boolean active, int expires) {
        ContentValues values = new ContentValues();
        values.put(SipProfile.FIELD_ID, id);
        values.put(SipProfile.FIELD_DISPLAY_NAME, displayName);
        values.put(SipProfile.FIELD_USERNAME, lineName);
        values.put(SipProfile.FIELD_DATATYPE, SipProfile.CRED_DATA_PLAIN_PASSWD);
        values.put(SipProfile.FIELD_DATA, password);
        values.put(SipProfile.FIELD_ACTIVE, active);
        contentProvider.insert(SipProfile.ACCOUNT_URI, values);
        ContentValues statusValues = new ContentValues();
        statusValues.put(SipProfileState.ACCOUNT_ID, id);
        statusValues.put(SipProfileState.ACTIVE, active);
        statusValues.put(SipProfileState.DISPLAY_NAME, displayName);
        statusValues.put(SipProfileState.EXPIRES, expires);
        contentProvider.insert(SipProfile.ACCOUNT_STATUS_URI, statusValues);
    }
    */

    public void testEnsureLineExistsDisabled() throws Exception {
        //insertProfileAndStatus(123, LINE_DISPLAY_NAME, "tbm0101", "blah", false, 0);
        assertNull("Ran CONFIGURE_LINE even though exists and disabled",
                startServiceAndGetBroadcast(new Intent(AcctDataService.ACTION_ENSURE_LINE),
                        AcctDataService.ACTION_CONFIGURE_LINE));
    }

    public void testEnsureLineExistsUp() throws Exception {
        //insertProfileAndStatus(321, LINE_DISPLAY_NAME, "tbm1010", "i'mup", true, 3600);
        assertNull("Ran CONFIGURE_LINE even though exists and up",
                startServiceAndGetBroadcast(new Intent(AcctDataService.ACTION_ENSURE_LINE),
                        AcctDataService.ACTION_CONFIGURE_LINE));
    }

    private Intent callEnsureLineAndCheckApiCalls(String acctName, String acctPw, String lineName,
                                                  String storedLinePw, String apiLinePw)
            throws InterruptedException, JSONException, IOException {
        setStoredAcct(acctName, acctPw);
        //insertProfileAndStatus(6, LINE_DISPLAY_NAME, lineName, storedLinePw, true, 0);
        when(fetcher.fetch(any(Map.class)))
                .thenReturn(new JSONObject().put("pw", apiLinePw));

        Intent resultIntent = startServiceAndGetBroadcast(
                new Intent(AcctDataService.ACTION_ENSURE_LINE),
                AcctDataService.ACTION_CONFIGURE_LINE);

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
