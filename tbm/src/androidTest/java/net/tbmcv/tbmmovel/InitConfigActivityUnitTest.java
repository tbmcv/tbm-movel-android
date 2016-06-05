package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.ContextWrapper;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.EditText;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InitConfigActivityUnitTest extends BaseActivityUnitTest<InitConfigActivity> {
    AcctDataService mockService;
    AcctDataService.Binder acctDataBinder;

    public InitConfigActivityUnitTest() {
        super(InitConfigActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TbmLinphoneConfigurator.instance = mock(TbmLinphoneConfigurator.class);
        mockService = mock(AcctDataService.class);
        acctDataBinder = new AcctDataService.Binder(mockService);
        getStartServiceTrap().setBoundService(AcctDataService.class, acctDataBinder);
    }

    @Override
    protected void tearDown() throws Exception {
        AnswerPromise.cleanup();
        super.tearDown();
        TbmLinphoneConfigurator.instance = new TbmLinphoneConfigurator();
    }

    @Override
    protected void launch() {
        super.launch();
        if (!LinphoneManager.isInstanciated()) {
            LinphoneManager.createAndStart(getActivity());
        }
        acctDataBinder.setReady();
    }

    private void performLogin(String username, String password) {
        enterText(R.id.usernameEntry, username);
        enterText(R.id.passwordEntry, password);
        View okButton = getActivity().findViewById(R.id.okButton);
        assertTrue(okButton.isEnabled());
        okButton.performClick();
        getInstrumentation().waitForIdleSync();
    }

    public void testLoginResetSuccessLaunchesMainActivity() throws Exception {
        launch();
        when(mockService.resetPassword(anyString(), anyString())).thenReturn(true);
        performLogin("9999999", "987654");
        new AssertWaiter() {
            @Override
            protected void test() throws Exception {
                getInstrumentation().waitForIdleSync();
                assertLaunched(LinphoneActivity.class);
                assertTrue("Finish not called", isFinishCalled());
            }
        }.await();
    }

    public void testControlsDisabled() throws Exception {
        launch();
        AnswerPromise<Boolean> resetPwPromise = new AnswerPromise<>();
        when(mockService.resetPassword(anyString(), anyString())).then(resetPwPromise);
        performLogin("9111111", "012345");
        assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
        assertFalse(getActivity().findViewById(R.id.usernameEntry).isEnabled());
        assertFalse(getActivity().findViewById(R.id.passwordEntry).isEnabled());
        assertFalse(getActivity().findViewById(R.id.helpButton).isEnabled());
        resetPwPromise.setResult(false);
    }

    public void testLoginPwResetRequestSent() throws Exception {
        launch();
        final String username = "9123456";
        final String password = "123321";
        performLogin(username, password);
        new AssertWaiter() {
            @Override
            protected void test() throws Exception {
                verify(mockService).resetPassword("c/" + username, password);
            }
        }.await();
    }

    public void testFailedLogin() throws Exception {
        launch();
        when(mockService.resetPassword(anyString(), anyString())).thenReturn(false);
        performLogin("9090909", "019840");
        final EditText passwordEntry = (EditText) getActivity().findViewById(R.id.passwordEntry);
        new AssertWaiter() {
            @Override
            protected void test() throws Exception {
                assertEquals("", passwordEntry.getText().toString());
                assertTrue(passwordEntry.isEnabled());
                assertTrue(getActivity().findViewById(R.id.usernameEntry).isEnabled());
                assertTrue(getActivity().findViewById(R.id.helpButton).isEnabled());
                assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
            }
        }.await();
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

    public void testOkButtonInitiallyDisabled() {
        launch();
        assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
    }

    public void testOkButtonDisabledNoPassword() {
        launch();
        enterText(R.id.usernameEntry, "9101019");
        assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
    }

    public void testOkButtonDisabledNoPhoneNumber() {
        launch();
        enterText(R.id.passwordEntry, "888222");
        assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
    }

    public void testOkButtonDisabledWrongPhoneNumberLength() {
        launch();
        enterText(R.id.usernameEntry, "18001234567");
        assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
    }

    public void testOkButtonDisabledShortPasswordLength() {
        launch();
        enterText(R.id.passwordEntry, "1357");
        assertFalse(getActivity().findViewById(R.id.okButton).isEnabled());
    }
}
