package net.tbmcv.tbmmovel;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.TextView;

import org.linphone.R;

public class SaldoFragmentUnitTest extends BaseActivityUnitTest<SaldoFragmentUnitTest.TestActivity> {
    public static final class TestActivity extends FragmentActivity {
        static final String FRAGMENT_TAG = "FragmentTag";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new SaldoFragment(), FRAGMENT_TAG)
                    .commit();
        }
    }

    public SaldoFragmentUnitTest() {
        super(TestActivity.class);
    }

    protected void startAndResumeAll() {
        getInstrumentation().callActivityOnStart(getActivity());
        getInstrumentation().callActivityOnResume(getActivity());
        for (Fragment fragment : getActivity().getSupportFragmentManager().getFragments()) {
            fragment.onStart();
            fragment.onResume();
        }
        getInstrumentation().waitForIdleSync();
        getActivity().getSupportFragmentManager().executePendingTransactions();
    }

    public void testSaldoRequestSent() {
        launch();
        startAndResumeAll();
        assertServiceStarted(AcctDataService.class, AcctDataService.ACTION_GET_CREDIT);
    }

    protected void checkSaldoResponseDisplayed(int saldo, String formattedSaldo) {
        launch();
        startAndResumeAll();
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
