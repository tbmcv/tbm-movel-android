package net.tbmcv.tbmmovel;

import android.net.Uri;
import android.test.TouchUtils;
import android.view.View;
import android.widget.EditText;

import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

public class InitConfigActivityTest extends BaseActivityTest<InitConfigActivity> {
    public InitConfigActivityTest() {
        super(InitConfigActivity.class);
    }

    private void enterText(int viewId, String text) {
        final View view = getActivity().findViewById(viewId);
        assertTrue(view.isEnabled());
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                view.requestFocus();
            }
        });
        getInstrumentation().waitForIdleSync();
        getInstrumentation().sendStringSync(text);
        getInstrumentation().waitForIdleSync();
        assertEquals(text, ((EditText) view).getText().toString());
    }

    private void performLogin(String username, String password) {
        enterText(R.id.usernameEntry, username);
        enterText(R.id.passwordEntry, password);
        View okButton = getActivity().findViewById(R.id.okButton);
        assertTrue(okButton.isEnabled());
        TouchUtils.clickView(this, okButton);
    }

    public void testRequestSent() throws Exception {
        JsonRestClient restClient = getRestClient();
        String username = "9123456";
        String password = "123454321";
        performLogin(username, password);
        InOrder inOrder = inOrder(restClient);
        inOrder.verify(restClient).setAuth("c/" + username, password);
        ArgumentCaptor<JSONObject> body = ArgumentCaptor.forClass(JSONObject.class);
        inOrder.verify(restClient).fetch(
                eq("POST"),
                eq(Uri.parse("/idens/c/" + username + "/pw")),
                body.capture(),
                any(JsonRestClient.Callback.class));
        assertEquals("base64", body.getValue().get("reset"));
        int size = body.getValue().getInt("size");
        assertTrue(size >= 4);
        assertTrue(size < 30);
    }

    public void testControlsDisabled() {
        performLogin("9111111", "012345");
        assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
        assertFalse(getActivity().findViewById(R.id.usernameEntry).isEnabled());
        assertFalse(getActivity().findViewById(R.id.passwordEntry).isEnabled());
        assertFalse(getActivity().findViewById(R.id.helpButton).isEnabled());
    }

    public void testFailedLogin() {
        performLogin("9555000", "314159");
        ArgumentCaptor<JsonRestClient.Callback> callback =
                ArgumentCaptor.forClass(JsonRestClient.Callback.class);
        verify(getRestClient()).fetch(
                any(String.class),
                any(Uri.class),
                any(JSONObject.class),
                callback.capture());
        callback.getValue().onFailure(new Exception("nope"));
        EditText passwordEntry = (EditText) getActivity().findViewById(R.id.passwordEntry);
        assertEquals("", passwordEntry.getText().toString());
        assertTrue(passwordEntry.isEnabled());
        assertTrue(getActivity().findViewById(R.id.okButton).isEnabled());
        assertTrue(getActivity().findViewById(R.id.usernameEntry).isEnabled());
        assertTrue(getActivity().findViewById(R.id.helpButton).isEnabled());
    }
}
