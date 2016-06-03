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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

public class SaldoFragment extends Fragment {
    private static final int UNKNOWN_CREDIT = Integer.MIN_VALUE;
    static final String LOG_TAG = "SaldoFragment";

    private NumberFormat creditFormat;
    private int currentCredit = UNKNOWN_CREDIT;

    private final LocalServiceConnection<AcctDataService> acctDataConnection =
            new LocalServiceConnection<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols();
        formatSymbols.setGroupingSeparator('.');
        creditFormat = new DecimalFormat("#,##0'$00'", formatSymbols);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        getContext().bindService(new Intent(getContext(), AcctDataService.class),
                acctDataConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
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
        loadCredit();
        ensureLine();
        onCreditUpdate();
    }

    protected void setCredit(int credit) {
        currentCredit = credit;
        onCreditUpdate();
    }

    protected void onCreditUpdate() {
        View view = getView();
        if (view != null) {
            ((TextView) view.findViewById(R.id.creditValue)).setText(
                    currentCredit == UNKNOWN_CREDIT ? "" : creditFormat.format(currentCredit));
        }
    }

    protected void loadCredit() {
        new AsyncTask<Object, Object, Integer>() {
            @Override
            protected Integer doInBackground(Object... params) {
                try {
                    return acctDataConnection.getService().getCredit();
                } catch (IOException|JSONException e) {
                    Activity activity = getActivity();
                    if (activity != null) {
                        Toast.makeText(activity,
                                R.string.tbm_login_error_net, Toast.LENGTH_LONG).show();
                    }
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Integer credit) {
                if (credit != null) {
                    setCredit(credit);
                }
            }
        }.execute();
    }

    protected void ensureLine() {
        new AsyncTask<Object, Object, Object>() {
            @Override
            protected Object doInBackground(Object... params) {
                try {
                    acctDataConnection.getService().ensureLine();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error ensuring line configured", e);
                }
                return null;
            }
        }.execute();
    }
}
