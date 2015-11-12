package net.tbmcv.tbmmovel;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.R;

public class InitConfigActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tbm_activity_init_config);

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);

        lbm.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                startActivity(new Intent(InitConfigActivity.this, MainActivity.class));
                finish();
            }
        }, new IntentFilter(AcctDataService.ACTION_PASSWORD_RESET));

        lbm.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int error_msg = 0;
                if (!intent.getBooleanExtra(AcctDataService.EXTRA_PASSWORD_OK, true)) {
                    error_msg = R.string.tbm_login_error_auth;
                    ((TextView) findViewById(R.id.passwordEntry)).setText("");
                } else if (!intent.getBooleanExtra(AcctDataService.EXTRA_CONNECTION_OK, true)) {
                    error_msg = R.string.tbm_login_error_net;
                }
                if (error_msg != 0) {
                    Toast.makeText(InitConfigActivity.this, error_msg, Toast.LENGTH_LONG).show();
                    setControlsEnabled(true);
                }
            }
        }, new IntentFilter(AcctDataService.ACTION_STATUS));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.tbm_menu_init_config, menu);
        return true;
    }

    private void setControlsEnabled(boolean enabled) {
        findViewById(R.id.okButton).setEnabled(enabled);
        findViewById(R.id.helpButton).setEnabled(enabled);
        findViewById(R.id.passwordEntry).setEnabled(enabled);
        findViewById(R.id.usernameEntry).setEnabled(enabled);
    }

    public void onOkButtonClick(View view) {
        setControlsEnabled(false);

        String acctName = "c/" + ((TextView) findViewById(R.id.usernameEntry)).getText();
        String tmpPw = ((TextView) findViewById(R.id.passwordEntry)).getText().toString();

        startService(new Intent(this, AcctDataService.class)
                .setAction(AcctDataService.ACTION_RESET_PASSWORD)
                .putExtra(AcctDataService.EXTRA_ACCT_NAME, acctName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, tmpPw));
    }
}
