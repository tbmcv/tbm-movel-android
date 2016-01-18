package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

public class SaldoFragment extends Fragment {
    private NumberFormat creditFormat;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols();
        formatSymbols.setGroupingSeparator('.');
        creditFormat = new DecimalFormat("#,##0'$00'", formatSymbols);
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tbm_fragment_saldo, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int credit = intent.getIntExtra(AcctDataService.EXTRA_CREDIT, Integer.MIN_VALUE);
                if (credit != Integer.MIN_VALUE) {
                    setCredit(credit);
                }
                if (!intent.getBooleanExtra(AcctDataService.EXTRA_CONNECTION_OK, true)) {
                    Toast.makeText(getActivity(),
                            R.string.tbm_login_error_net, Toast.LENGTH_LONG).show();
                }
            }
        }, new IntentFilter(AcctDataService.ACTION_STATUS));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCredit();
        ensureLine();
    }

    protected void setCredit(int credit) {
        ((TextView) getView().findViewById(R.id.creditValue)).setText(creditFormat.format(credit));
    }

    protected void loadCredit() {
        Activity activity = getActivity();
        activity.startService(new Intent(activity, AcctDataService.class)
                .setAction(AcctDataService.ACTION_GET_CREDIT));
    }

    protected void ensureLine() {
        Activity activity = getActivity();
        activity.startService(new Intent(activity, AcctDataService.class)
                .setAction(AcctDataService.ACTION_ENSURE_LINE));
    }
}
