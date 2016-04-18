package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.EditText;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertLaunched(LinphoneActivity.class);
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

        Intent intent = getStartServiceTrap().getServiceStarted(
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

    public void testGSMEnabled() {
        launch();
        LinphoneCore lc = LinphoneManager.getLc();
        assertTrue(lc.isPayloadTypeEnabled(lc.findPayloadType("GSM", 8000)));
    }

    public void testNoAdaptiveRateControl() {
        launch();
        LinphoneCore lc = LinphoneManager.getLc();
        assertFalse(lc.isAdaptiveRateControlEnabled());
    }

    private TelephonyManager mockTelephonyManager(String line1Number) {
        final TelephonyManager telephonyManager = mock(TelephonyManager.class);
        setActivityContext(new ContextWrapper(getStartServiceTrap()) {
            @Override
            public Object getSystemService(String name) {
                if (Context.TELEPHONY_SERVICE.equals(name)) {
                    return telephonyManager;
                } else {
                    return super.getSystemService(name);
                }
            }
        });
        when(telephonyManager.getLine1Number()).thenReturn(line1Number);
        return telephonyManager;
    }

    public void testInitialPhoneNumberCV() {
        String number = "9334456";
        mockTelephonyManager("238" + number);
        launch();
        assertEquals(number,
                ((EditText) getActivity().findViewById(R.id.usernameEntry)).getText().toString());
    }

    public void testInitialPhoneNumberUnknown() {
        mockTelephonyManager(null);
        launch();
        assertEquals("",
                ((EditText) getActivity().findViewById(R.id.usernameEntry)).getText().toString());
    }

    public void testInitialPhoneNumberUSA() {
        mockTelephonyManager("15555555555");
        launch();
        assertEquals("",
                ((EditText) getActivity().findViewById(R.id.usernameEntry)).getText().toString());
    }
}
