package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

public class MainActivity extends Activity {
    private NumberFormat creditFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols();
        formatSymbols.setGroupingSeparator('.');
        creditFormat = new DecimalFormat("#,##0'$00'", formatSymbols);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int credit = intent.getIntExtra(AcctDataService.EXTRA_CREDIT, Integer.MIN_VALUE);
                if (credit != Integer.MIN_VALUE) {
                    setCredit(credit);
                }
                if (!intent.getBooleanExtra(AcctDataService.EXTRA_CONNECTION_OK, true)) {
                    Toast.makeText(MainActivity.this,
                            R.string.login_error_net, Toast.LENGTH_LONG).show();
                }
            }
        }, new IntentFilter(AcctDataService.ACTION_STATUS));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCredit();
    }

    protected void setCredit(int credit) {
        ((TextView) findViewById(R.id.creditValue)).setText(creditFormat.format(credit));
    }

    protected void loadCredit() {
        startService(new Intent(this, AcctDataService.class)
                .setAction(AcctDataService.ACTION_GET_CREDIT));
    }
}
