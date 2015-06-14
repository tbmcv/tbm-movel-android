package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

public class MainActivity extends BaseActivity {
    private NumberFormat creditFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols();
        formatSymbols.setGroupingSeparator('.');
        creditFormat = new DecimalFormat("#,##0'$00'", formatSymbols);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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

    protected void onNotConfigured() {
        startActivity(new Intent(MainActivity.this, InitConfigActivity.class));
        finish();
    }

    protected void setCredit(int credit) {
        ((TextView) findViewById(R.id.creditValue)).setText(creditFormat.format(credit));
    }

    protected void loadCredit() {
        SharedPreferences config = getSharedPreferences(
                getString(R.string.settings_key), Context.MODE_PRIVATE);
        String acctName = config.getString(getString(R.string.setting_acctname), null);
        String password = config.getString(getString(R.string.setting_password), null);
        if (acctName == null || password == null) {
            onNotConfigured();
            return;
        }
        final JsonRestClient restClient = getRestClient();
        restClient.setAuth(acctName, password);
        restClient.fetch("GET", Uri.parse("/idens/" + acctName + "/saldo/"),
                null, new JsonRestClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                final int credit;
                try {
                    credit = result.getInt("saldo");
                } catch (JSONException e) {
                    throw new RuntimeException(e);  // TODO handle nicely
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setCredit(credit);
                    }
                });
            }

            @Override
            public void onFailure(Exception err) {
                throw new RuntimeException(err);  // TODO if wrong auth, call onNotConfigured()
            }
        });
    }
}
