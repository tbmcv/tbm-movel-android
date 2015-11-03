package net.tbmcv.tbmmovel;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.TextView;

public class MainActivityUnitTest extends BaseActivityUnitTest<MainActivity> {
    public MainActivityUnitTest() {
        super(MainActivity.class);
    }

    public void testSaldoRequestSent() {
        launch();
        getInstrumentation().callActivityOnResume(getActivity());
        getInstrumentation().waitForIdleSync();
        assertServiceStarted(AcctDataService.class, AcctDataService.ACTION_GET_CREDIT);
    }

    protected void checkSaldoResponseDisplayed(int saldo, String formattedSaldo) {
        launch();
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
                new Intent(AcctDataService.ACTION_STATUS)
                        .putExtra(AcctDataService.EXTRA_CREDIT, saldo));
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
