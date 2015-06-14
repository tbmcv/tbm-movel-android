package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class MainActivityUnitTest extends BaseActivityUnitTest<MainActivity> {
    public MainActivityUnitTest() {
        super(MainActivity.class);
    }

    public void testActivitySwitchesIfUnconfigured() {
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launch();
        getInstrumentation().callActivityOnResume(getActivity());
        getInstrumentation().waitForIdleSync();
        assertLaunched(InitConfigActivity.class);
        assertTrue("Finish not called", isFinishCalled());
    }

    private void setStoredAcct(String acctName, String pw) {
        Context context = getInstrumentation().getTargetContext();
        context.getSharedPreferences(context.getString(R.string.settings_key), Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putString(context.getString(R.string.setting_acctname), acctName)
                .putString(context.getString(R.string.setting_password), pw)
                .commit();
    }

    public void testNoActivitySwitchIfConfigured() {
        setStoredAcct("c/9345678", "blah");
        launch();
        getInstrumentation().callActivityOnResume(getActivity());
        getInstrumentation().waitForIdleSync();
        assertNull("Another Activity started", getStartedActivityIntent());
        assertFalse("Finish called", isFinishCalled());
    }

    public void testSaldoRequestSent() {
        String acctName = "c/5050505";
        String pw = "anything";
        setStoredAcct(acctName, pw);
        launch();
        getInstrumentation().callActivityOnResume(getActivity());
        getInstrumentation().waitForIdleSync();
        JsonRestClient restClient = getRestClient();
        InOrder inOrder = inOrder(restClient);
        inOrder.verify(restClient).setAuth(acctName, pw);
        inOrder.verify(restClient).fetch(
                eq("GET"),
                eq(Uri.parse("/idens/" + acctName + "/saldo/")),
                isNull(JSONObject.class),
                any(JsonRestClient.Callback.class));
    }

    protected void checkSaldoResponseDisplayed(int saldo, String formattedSaldo) {
        setStoredAcct("c/9995555", "sosecret");
        launch();
        getInstrumentation().callActivityOnResume(getActivity());
        getInstrumentation().waitForIdleSync();
        ArgumentCaptor<JsonRestClient.Callback> callbackCaptor =
                ArgumentCaptor.forClass(JsonRestClient.Callback.class);
        verify(getRestClient()).fetch(
                anyString(),
                any(Uri.class),
                any(JSONObject.class),
                callbackCaptor.capture());
        JSONObject result;
        try {
            result = new JSONObject().put("saldo", saldo);
        } catch (JSONException e) {
            throw new Error(e);
        }
        callbackCaptor.getValue().onSuccess(result);
        getInstrumentation().waitForIdleSync();
        TextView creditView = (TextView) getActivity().findViewById(R.id.creditValue);
        assertEquals(formattedSaldo, creditView.getText().toString().trim());
    }

    public void testSaldoResponseDisplayedZero() {
        checkSaldoResponseDisplayed(0, "0$00");
    }

    public void testSaldoResponseDisplayedSmall() {
        checkSaldoResponseDisplayed(17, "17$00");
    }

    public void testSaldoResponseDisplayedLarge() {
        checkSaldoResponseDisplayed(1234, "1.234$00");
    }

    public void testSaldoResponseDisplayedHuge() {
        checkSaldoResponseDisplayed(2147483647, "2.147.483.647$00");
    }

    public void testSaldoResponseDisplayedNegativeSmall() {
        checkSaldoResponseDisplayed(-3, "-3$00");
    }

    public void testSaldoResponseDisplayedNegativeLarge() {
        checkSaldoResponseDisplayed(-99876, "-99.876$00");
    }
}
