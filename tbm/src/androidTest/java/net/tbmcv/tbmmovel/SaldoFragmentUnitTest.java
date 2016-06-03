package net.tbmcv.tbmmovel;

import android.widget.TextView;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SaldoFragmentUnitTest extends BaseFragmentUnitTest<SaldoFragment> {
    @Override
    protected SaldoFragment createFragment() {
        return new SaldoFragment();
    }

    AcctDataService mockService;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockService = mock(AcctDataService.class);
        getStartServiceTrap().setBoundService(
                AcctDataService.class,
                new AcctDataService.Binder(mockService));
    }

    public void testEnsureLineCalled() throws Exception {
        startAndResumeAll();
        new AssertWaiter() {
            @Override
            protected void test() throws Exception {
                verify(mockService).ensureLine();
            }
        }.await();
    }

    protected void checkSaldoResponseDisplayed(int saldo, final String formattedSaldo)
            throws Exception {
        when(mockService.getCredit()).thenReturn(saldo);
        startAndResumeAll();
        final TextView creditView = (TextView) getActivity().findViewById(R.id.creditValue);
        new AssertWaiter() {
            @Override
            protected void test() {
                getInstrumentation().waitForIdleSync();
                assertEquals(formattedSaldo, creditView.getText().toString().trim());
            }
        }.await();
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
}
