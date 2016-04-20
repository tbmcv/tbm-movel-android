package net.tbmcv.tbmmovel;
/*
InitConfigActivity.java
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListenerBase;

public class InitConfigActivity extends FragmentActivity {
    static final String LOG_TAG = "InitConfigActivity";

    private LocalBroadcastReceiverManager localReceivers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tbm_activity_init_config);

        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String number = telephonyManager.getLine1Number();
        if (number != null && number.length() == 10 && number.startsWith("238")) {
            ((EditText) findViewById(R.id.usernameEntry)).setText(number.substring(3));
        }

        localReceivers = new LocalBroadcastReceiverManager(this);

        localReceivers.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                startActivity(new Intent(context, LinphoneActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                finish();
            }
        }, new IntentFilter(AcctDataService.ACTION_PASSWORD_RESET));

        localReceivers.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int error_msg = 0;
                if (!intent.getBooleanExtra(AcctDataService.EXTRA_PASSWORD_OK, true)) {
                    error_msg = R.string.tbm_login_error_auth;
                    ((EditText) findViewById(R.id.passwordEntry)).setText("");
                } else if (!intent.getBooleanExtra(AcctDataService.EXTRA_CONNECTION_OK, true)) {
                    error_msg = R.string.tbm_login_error_net;
                }
                if (error_msg != 0) {
                    Toast.makeText(context, error_msg, Toast.LENGTH_LONG).show();
                    setControlsEnabled(true);
                }
            }
        }, new IntentFilter(AcctDataService.ACTION_STATUS));

        TextWatcher validator = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                setControlsEnabled(true);
            }
        };
        ((EditText) findViewById(R.id.usernameEntry)).addTextChangedListener(validator);
        ((EditText) findViewById(R.id.passwordEntry)).addTextChangedListener(validator);
        setControlsEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        localReceivers.unregisterAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.terminateAllCalls();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.tbm_menu_init_config, menu);
        return true;
    }

    private void setControlsEnabled(boolean enabled) {
        findViewById(R.id.okButton).setEnabled(enabled && inputValid());
        findViewById(R.id.helpButton).setEnabled(enabled);
        findViewById(R.id.passwordEntry).setEnabled(enabled);
        findViewById(R.id.usernameEntry).setEnabled(enabled);
    }

    private boolean inputValid() {
        String phoneNumber = ((EditText) findViewById(R.id.usernameEntry)).getText().toString();
        if (phoneNumber.length() != 7) {
            return false;
        }
        String tmpPw = ((EditText) findViewById(R.id.passwordEntry)).getText().toString();
        return tmpPw.length() >= 6;
    }

    public void onOkButtonClick(View view) {
        setControlsEnabled(false);

        String acctName = "c/" + ((EditText) findViewById(R.id.usernameEntry)).getText();
        String tmpPw = ((EditText) findViewById(R.id.passwordEntry)).getText().toString();

        try {
            TbmLinphoneConfigurator.getInstance().setDefaultSettings();
            startEchoCalibration();
        } catch (LinphoneCoreException e) {
            Log.e(LOG_TAG, "Error setting default settings", e);
        }

        startService(new Intent(this, AcctDataService.class)
                .setAction(AcctDataService.ACTION_RESET_PASSWORD)
                .putExtra(AcctDataService.EXTRA_ACCT_NAME, acctName)
                .putExtra(AcctDataService.EXTRA_PASSWORD, tmpPw));
    }

    public void onHelpButtonClick(View view) {
        new HelpDialogFragment().show(getSupportFragmentManager(), "help");
    }

    public void onExitButtonClick(View view) {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.terminateAllCalls();
        }
        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().exit();
        } else {
            stopService(new Intent(this, LinphoneService.class));
            LinphoneManager.destroy();
        }
        finish();
    }

    protected void startEchoCalibration() throws LinphoneCoreException {
        TbmLinphoneConfigurator.getInstance().startEchoCalibration(new LinphoneCoreListenerBase() {
            @Override
            public void ecCalibrationStatus(LinphoneCore lc,
                                            LinphoneCore.EcCalibratorStatus status,
                                            int delay_ms, Object data) {
                onEchoCalibrationComplete(status);
            }
        });
    }

    protected void onEchoCalibrationComplete(LinphoneCore.EcCalibratorStatus status) {
        int message = 0;
        switch (status.value()) {
            case LinphoneCore.EcCalibratorStatus.FAILED_STATUS:
                message = R.string.tbm_echo_calibration_failure;
                break;
            case LinphoneCore.EcCalibratorStatus.DONE_STATUS:
            case LinphoneCore.EcCalibratorStatus.DONE_NO_ECHO_STATUS:
                message = R.string.tbm_echo_calibration_success;
                break;
        }
        if (message != 0) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    public static class HelpDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.tbm_init_config_help_explanation)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) { }
                    })
                    .create();
        }
    }
}
