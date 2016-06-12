package net.tbmcv.tbmmovel;
/*
SaldoFragment.java
Copyright (C) 2016  TBM Comunicações, Lda.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

import static net.tbmcv.tbmmovel.SaldoService.UNKNOWN_CREDIT;

public class SaldoFragment extends Fragment {
    private NumberFormat creditFormat;

    private LocalServiceConnection<AcctDataService> acctDataConnection;
    private LocalServiceConnection<SaldoService> saldoConnection;

    private final BroadcastReceiver saldoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onCreditUpdate(intent.getIntExtra(SaldoService.EXTRA_CREDIT, UNKNOWN_CREDIT));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols();
        formatSymbols.setGroupingSeparator('.');
        creditFormat = new DecimalFormat("#,##0'$00'", formatSymbols);
        super.onCreate(savedInstanceState);
        acctDataConnection = new LocalServiceConnection<>();
        saldoConnection = new LocalServiceConnection<>();
        acctDataConnection.addListener(new LocalServiceListener<AcctDataService>() {
            @Override
            public void serviceConnected(AcctDataService service) {
                if (isResumed()) {
                    service.checkLine();
                }
            }

            @Override
            public void serviceDisconnected() { }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(
                saldoReceiver, new IntentFilter(SaldoService.ACTION_UPDATE));
        saldoConnection.bind(getContext(), SaldoService.class);
        acctDataConnection.bind(getContext(), AcctDataService.class);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(saldoReceiver);
        saldoConnection.unbind(getContext());
        acctDataConnection.unbind(getContext());
        super.onStop();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tbm_fragment_saldo, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        onCreditUpdate();
        if (acctDataConnection.isBound()) {
            acctDataConnection.getService().checkLine();
        }
    }

    protected void onCreditUpdate(int currentCredit) {
        View view = getView();
        if (view != null) {
            ((TextView) view.findViewById(R.id.creditValue)).setText(
                    currentCredit == UNKNOWN_CREDIT ? "" : creditFormat.format(currentCredit));
        }
    }

    protected void onCreditUpdate() {
        if (getView() != null && saldoConnection.isBound()) {
            onCreditUpdate(saldoConnection.getService().getCredit());
        }
    }
}
