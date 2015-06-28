package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AcctDataServiceTest extends BaseServiceUnitTest<AcctDataService> {
    public AcctDataServiceTest() {
        super(AcctDataService.class);
    }

    private MockJrcRequest.Fetcher fetcher;
    private ArgumentCaptor<Map<String, ?>> paramsCaptor;
    private CountDownLatch startFetchLatch;
    private CountDownLatch finishFetchLatch;

    @Override
    protected void setUp() throws Exception {
        fetcher = MockJrcRequest.mockDefaultClient();
        paramsCaptor = ArgumentCaptor.forClass((Class) Map.class);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        unpauseFetchResults();
        super.tearDown();
    }

    protected void pauseFetchResults(final JSONObject result) {
        unpauseFetchResults();
        final CountDownLatch startFetchLatch = new CountDownLatch(1);
        final CountDownLatch finishFetchLatch = new CountDownLatch(1);
        this.startFetchLatch = startFetchLatch;
        this.finishFetchLatch = finishFetchLatch;
        when(fetcher.fetch(any(Map.class))).then(new Answer<JSONObject>() {
            @Override
            public JSONObject answer(InvocationOnMock invocation) throws Throwable {
                startFetchLatch.countDown();
                finishFetchLatch.await(10, TimeUnit.SECONDS);
                return result;
            }
        });
    }

    protected void pauseFetchResults() {
        pauseFetchResults(new JSONObject());
    }

    protected void unpauseFetchResults() {
        if (finishFetchLatch != null) {
            finishFetchLatch.countDown();
        }
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
        pauseFetchResults();
        startService(new Intent(AcctDataService.ACTION_GET_CREDIT));
        startFetchLatch.await(2, TimeUnit.SECONDS);

        verify(fetcher).fetch(paramsCaptor.capture());
        Map<String, ?> params = paramsCaptor.getValue();
        assertEquals(acctName, params.get("username"));
        assertEquals(pw, params.get("password"));
        assertEquals("GET", params.get("method"));
        assertEquals(URI.create("/idens/" + acctName + "/saldo/"),
                URI.create("/").resolve((URI) params.get("uri")));
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
        pauseFetchResults();
        startService(new Intent(AcctDataService.ACTION_RESET_PASSWORD)
                .putExtra(AcctDataService.EXTRA_ACCT_NAME, username)
                .putExtra(AcctDataService.EXTRA_PASSWORD, password));
        startFetchLatch.await(2, TimeUnit.SECONDS);

        verify(fetcher).fetch(paramsCaptor.capture());
        Map<String, ?> params = paramsCaptor.getValue();
        assertEquals(username, params.get("username"));
        assertEquals(password, params.get("password"));
        assertEquals("POST", params.get("method"));
        assertEquals(URI.create("/idens/" + username + "/pw"),
                URI.create("/").resolve((URI) params.get("uri")));
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
}
