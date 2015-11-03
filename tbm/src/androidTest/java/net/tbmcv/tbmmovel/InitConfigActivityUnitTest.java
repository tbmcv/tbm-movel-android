package net.tbmcv.tbmmovel;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.EditText;

public class InitConfigActivityUnitTest extends BaseActivityUnitTest<InitConfigActivity> {
    public InitConfigActivityUnitTest() {
        super(InitConfigActivity.class);
    }

    private void fakeSuccessfulLogin() {
        getActivity().findViewById(R.id.okButton).performClick();
        getInstrumentation().waitForIdleSync();
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
                new Intent(AcctDataService.ACTION_PASSWORD_RESET));
        getInstrumentation().waitForIdleSync();
    }

    private void performLogin(String username, String password) {
        enterText(R.id.usernameEntry, username);
        enterText(R.id.passwordEntry, password);
        View okButton = getActivity().findViewById(R.id.okButton);
        assertTrue(okButton.isEnabled());
        okButton.performClick();
        getInstrumentation().waitForIdleSync();
    }

    public void testLoginResetSuccessLaunchesMainActivity() {
        launch();
        fakeSuccessfulLogin();
        assertLaunched(MainActivity.class);
        assertTrue("Finish not called", isFinishCalled());
    }

    public void testControlsDisabled() {
        launch();
        performLogin("9111111", "012345");
        assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
        assertFalse(getActivity().findViewById(R.id.usernameEntry).isEnabled());
        assertFalse(getActivity().findViewById(R.id.passwordEntry).isEnabled());
        assertFalse(getActivity().findViewById(R.id.helpButton).isEnabled());
    }

    public void testLoginPwResetRequestSent() {
        launch();
        String username = "9123456";
        String password = "123454321";
        performLogin(username, password);

        Intent intent = assertServiceStarted(
                AcctDataService.class, AcctDataService.ACTION_RESET_PASSWORD);
        assertEquals("c/" + username, intent.getStringExtra(AcctDataService.EXTRA_ACCT_NAME));
        assertEquals(password, intent.getStringExtra(AcctDataService.EXTRA_PASSWORD));
    }

    public void testFailedLogin() {
        launch();
        performLogin("9090909", "1984");
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
                new Intent(AcctDataService.ACTION_STATUS)
                        .putExtra(AcctDataService.EXTRA_PASSWORD_OK, false));
        getInstrumentation().waitForIdleSync();

        EditText passwordEntry = (EditText) getActivity().findViewById(R.id.passwordEntry);
        assertEquals("", passwordEntry.getText().toString());
        assertTrue(passwordEntry.isEnabled());
        assertTrue(getActivity().findViewById(R.id.okButton).isEnabled());
        assertTrue(getActivity().findViewById(R.id.usernameEntry).isEnabled());
        assertTrue(getActivity().findViewById(R.id.helpButton).isEnabled());
    }
}
