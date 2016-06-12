package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.TextView;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SaldoFragmentUnitTest extends BaseFragmentUnitTest<SaldoFragment> {
    @Override
    protected SaldoFragment createFragment() {
        return new SaldoFragment();
    }

    AcctDataService mockAcctDataService;
    AcctDataService.Binder acctDataBinder;
    SaldoService mockSaldoService;
    SaldoService.Binder saldoBinder;

    protected void setUp() throws Exception {
        super.setUp();
        mockAcctDataService = mock(AcctDataService.class);
        acctDataBinder = new AcctDataService.Binder(mockAcctDataService);
        getStartServiceTrap().setBoundService(AcctDataService.class, acctDataBinder);
        mockSaldoService = mock(SaldoService.class);
        saldoBinder = new SaldoService.Binder(mockSaldoService);
        getStartServiceTrap().setBoundService(SaldoService.class, saldoBinder);
    }

    @Override
    protected void startAndResumeAll() {
        super.startAndResumeAll();
        acctDataBinder.setReady();
        saldoBinder.setReady();
    }

    public void testEnsureLineCalled() throws Exception {
        startAndResumeAll();
        verify(mockAcctDataService, timeout(2000).atLeastOnce()).checkLine();
    }

    protected void assertSaldoDisplayChangesTo(final String formattedSaldo) throws Exception {
        final TextView creditView = (TextView) getActivity().findViewById(R.id.creditValue);
        new AssertWaiter() {
            @Override
            protected void test() {
                getInstrumentation().waitForIdleSync();
                assertEquals(formattedSaldo, creditView.getText().toString().trim());
            }
        }.await();
    }

    protected void sendSaldoUpdate(int saldo) {
        when(mockSaldoService.getCredit()).thenReturn(saldo);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
                new Intent(SaldoService.ACTION_UPDATE).putExtra(SaldoService.EXTRA_CREDIT, saldo));
    }

    protected void checkSaldoResponseDisplayed(int saldo, final String formattedSaldo)
            throws Exception {
        startAndResumeAll();
        sendSaldoUpdate(saldo);
        assertSaldoDisplayChangesTo(formattedSaldo);
    }

    public void testSaldoResponseDisplayedZero() throws Exception {
        checkSaldoResponseDisplayed(0, "0$00");
    }

    public void testSaldoResponseDisplayedSmall() throws Exception {
        checkSaldoResponseDisplayed(17, "17$00");
    }

    public void testSaldoResponseDisplayedLarge() throws Exception {
        checkSaldoResponseDisplayed(1234, "1.234$00");
    }

    public void testSaldoResponseDisplayedHuge() throws Exception {
        checkSaldoResponseDisplayed(2147483647, "2.147.483.647$00");
    }

    public void testSaldoResponseDisplayedNegativeSmall() throws Exception {
        checkSaldoResponseDisplayed(-3, "-3$00");
    }

    public void testSaldoResponseDisplayedNegativeLarge() throws Exception {
        checkSaldoResponseDisplayed(-99876, "-99.876$00");
    }

    public void testSaldoDisplayAfterResume() throws Exception {
        startAndResumeAll();
        sendSaldoUpdate(23);
        getInstrumentation().callActivityOnPause(getActivity());
        sendSaldoUpdate(75);
        resumeActivity();
        assertSaldoDisplayChangesTo("75$00");
    }

    public void testSaldoDisplayAfterStopAndStart() throws Exception {
        startAndResumeAll();
        sendSaldoUpdate(-2);
        Activity activity = getActivity();
        getInstrumentation().callActivityOnPause(activity);
        getInstrumentation().callActivityOnStop(activity);
        sendSaldoUpdate(130);
        getInstrumentation().callActivityOnStart(activity);
        resumeActivity();
        assertSaldoDisplayChangesTo("130$00");
    }
}
