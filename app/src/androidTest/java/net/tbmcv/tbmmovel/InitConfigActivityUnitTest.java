package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

public class InitConfigActivityUnitTest extends BaseActivityUnitTest<InitConfigActivity> {
    public InitConfigActivityUnitTest() {
        super(InitConfigActivity.class);
    }

    private void fakeSuccessfulLogin(String pw) {
        getActivity().findViewById(R.id.okButton).performClick();
        ArgumentCaptor<JsonRestClient.Callback> callbackCaptor =
                ArgumentCaptor.forClass(JsonRestClient.Callback.class);
        verify(getRestClient()).fetch(
                anyString(), any(Uri.class), any(JSONObject.class), callbackCaptor.capture());
        JSONObject result;
        try {
            result = new JSONObject().put("pw", pw);
        } catch (JSONException e) {
            throw new Error(e);
        }
        callbackCaptor.getValue().onSuccess(result);
        getInstrumentation().waitForIdleSync();
    }

    public void testLoginResetSuccessLaunchesMainActivity() {
        launch();
        fakeSuccessfulLogin("new-pw123");
        assertLaunched(MainActivity.class);
        assertTrue("Finish not called", isFinishCalled());
    }

    public void testLoginResetSuccessSavesCreds() {
        String phoneNumber = "9999999";
        String newPw = "gg";
        launch();
        ((TextView) getActivity().findViewById(R.id.usernameEntry)).setText(phoneNumber);
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        fakeSuccessfulLogin(newPw);
        assertEquals("c/" + phoneNumber,
                prefs.getString(context.getString(R.string.setting_acctname), "<NOTHING STORED>"));
        assertEquals(newPw,
                prefs.getString(context.getString(R.string.setting_password), "<NOTHING STORED>"));
    }
}
