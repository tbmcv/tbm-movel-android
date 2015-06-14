package net.tbmcv.tbmmovel;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

public class InitConfigActivityUnitTest extends BaseActivityUnitTest<InitConfigActivity> {
    public InitConfigActivityUnitTest() {
        super(InitConfigActivity.class);
    }

    public void testLoginResetSuccessLaunchesMainActivity() throws JSONException {
        getActivity().findViewById(R.id.okButton).performClick();
        ArgumentCaptor<JsonRestClient.Callback> callbackCaptor =
                ArgumentCaptor.forClass(JsonRestClient.Callback.class);
        verify(getRestClient()).fetch(
                anyString(), any(Uri.class), any(JSONObject.class), callbackCaptor.capture());
        callbackCaptor.getValue().onSuccess(new JSONObject().put("pw", "new-pw123"));
        assertLaunched(MainActivity.class);
        assertTrue("Finish not called", isFinishCalled());
    }
}
