package net.tbmcv.tbmmovel;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import com.csipsimple.api.SipProfile;
import com.csipsimple.api.SipProfileState;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AcctDataServiceTest extends BaseServiceUnitTest<AcctDataService> {
    public AcctDataServiceTest() {
        super(AcctDataService.class);
    }

    private MockJrcRequest.Fetcher fetcher;
    private ArgumentCaptor<Map<String, ?>> paramsCaptor;
    private TestingContentProvider contentProvider;

    protected void setUp() throws Exception {
        fetcher = MockJrcRequest.mockDefaultClient();
        paramsCaptor = ArgumentCaptor.forClass((Class) Map.class);
        super.setUp();
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
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        AnswerPromise.cleanup();
    }

    protected void setStoredAcct(String acctName, String pw) {
        Context context = getContext();
        context.getSharedPreferences(context.getString(R.string.settings_key), Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putString(context.getString(R.string.setting_acctname), acctName)
                .putString(context.getString(R.string.setting_password), pw)
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
                context.getString(R.string.settings_key), Context.MODE_PRIVATE);
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
                context.getString(R.string.settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        when(fetcher.fetch(any(Map.class))).thenReturn(new JSONObject().put("pw", newPw));
        startServiceAndWaitForBroadcast(new Intent(AcctDataService.ACTION_RESET_PASSWORD)
                        .putExtra(AcctDataService.EXTRA_ACCT_NAME, "c/" + phoneNumber)
                        .putExtra(AcctDataService.EXTRA_PASSWORD, "g2g"),
                AcctDataService.ACTION_STATUS, AcctDataService.ACTION_PASSWORD_RESET);

        assertEquals("c/" + phoneNumber,
                prefs.getString(context.getString(R.string.setting_acctname), "(NOTHING STORED)"));
        assertEquals(newPw,
                prefs.getString(context.getString(R.string.setting_password), "(NOTHING STORED)"));
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
        final String realm = getContext().getString(R.string.sip_realm);
        final String displayName = getContext().getString(R.string.csipsimple_display_name);
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
    }

    public void testConfigureNewVoipLine() {
        String lineName = "tbm9999";
        String password = "pass";
        startService(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        checkVoipLine(lineName, password);
    }

    public void testReconfigureVoipLine() {
        String lineName = "tbm5555";
        String password = "*****";
        ContentValues oldValues = new ContentValues();
        oldValues.put(SipProfile.FIELD_DISPLAY_NAME, "TBM Móvel (auto)");
        contentProvider.insert(SipProfile.ACCOUNT_URI, oldValues);
        startService(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        checkVoipLine(lineName, password);
    }

    void checkOtherVoipLinesLeft(String lineName, String password) {
        String otherDisplayName = "leave me alone!";
        String otherAccId = "sip:leave@me.alone.net";
        ContentValues oldValues = new ContentValues();
        oldValues.put(SipProfile.FIELD_DISPLAY_NAME, otherDisplayName);
        oldValues.put(SipProfile.FIELD_ACC_ID, otherAccId);
        Uri otherUri = contentProvider.insert(SipProfile.ACCOUNT_URI, oldValues);
        startService(new Intent(AcctDataService.ACTION_CONFIGURE_LINE)
                .putExtra(AcctDataService.EXTRA_LINE_NAME, lineName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
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
    }

    public void testNewLineLeavesOtherVoipLines() {
        checkOtherVoipLinesLeft("tbm5432", "lalala");
    }

    public void testReconfigureLeavesOtherVoipLines() {
        ContentValues oldValues = new ContentValues();
        oldValues.put(SipProfile.FIELD_DISPLAY_NAME, "TBM Móvel (auto)");
        checkOtherVoipLinesLeft("tbm1111", "abcdefg");
    }

    private static void assertUriEquals(URI expected, Object actual) {
        assertEquals(expected, URI.create("/").resolve((URI) actual));
    }

    private static void assertUriEquals(String expected, Object actual) {
        assertUriEquals(URI.create(expected), actual);
    }
}
