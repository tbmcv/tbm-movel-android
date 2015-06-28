package net.tbmcv.tbmmovel;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

public class AcctDataService extends IntentService {
    public static final String ACTION_RESET_PASSWORD = "net.tbmcv.tbmmovel.RESET_PASSWORD";
    public static final String ACTION_GET_CREDIT = "net.tbmcv.tbmmovel.GET_CREDIT";
    public static final String ACTION_STATUS = "net.tbmcv.tbmmovel.STATUS";
    public static final String ACTION_PASSWORD_RESET = "net.tbmcv.tbmmovel.PASSWORD_RESET";
    public static final String EXTRA_ACCT_NAME = "net.tbmcv.tbmmovel.ACCT_NAME";
    public static final String EXTRA_PASSWORD = "net.tbmcv.tbmmovel.PASSWORD";
    public static final String EXTRA_CONNECTION_OK = "net.tbmcv.tbmmovel.CONNECTION_OK";
    public static final String EXTRA_PASSWORD_OK = "net.tbmcv.tbmmovel.PASSWORD_OK";
    public static final String EXTRA_CREDIT = "net.tbmcv.tbmmovel.CREDIT";

    public AcctDataService() {
        super("AcctDataService");
    }

    protected JsonRestClient getRestClient() {
        return JsonRestClientFactory.Default.get().getRestClient(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_GET_CREDIT:
                onCommandGetCredit();
                break;
            case ACTION_RESET_PASSWORD:
                onCommandResetPassword(
                        intent.getStringExtra(EXTRA_ACCT_NAME),
                        intent.getStringExtra(EXTRA_PASSWORD));
                break;
        }
    }

    private void onCommandGetCredit() {
        SharedPreferences config = getSharedPreferences(
                getString(R.string.settings_key), Context.MODE_PRIVATE);
        String acctName = config.getString(getString(R.string.setting_acctname), null);
        String password = config.getString(getString(R.string.setting_password), null);
        if (acctName == null || password == null) {
            startActivity(new Intent(this, InitConfigActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent status = new Intent(ACTION_STATUS);
        try {
            JSONObject result = getRestClient().buildRequest()
                    .auth(acctName, password)
                    .toUri("idens/")
                    .toUri(acctName + "/")
                    .toUri("saldo/")
                    .build()
                    .fetch();
            status.putExtra(EXTRA_CREDIT, result.getInt("saldo"));
        } catch (JSONException e) {  // TODO other types of errors
            status.putExtra(EXTRA_CONNECTION_OK, false);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(status);
    }

    private void onCommandResetPassword(String acctName, String password) {
        try {
            JSONObject result = getRestClient().buildRequest()
                    .auth(acctName, password)
                    .toUri("idens/")
                    .toUri(acctName + "/")
                    .toUri("pw")
                    .method("POST")
                    .body(new JSONObject().put("reset", "base64").put("size", 4))
                    .build()
                    .fetch();
            String newPw = result.getString("pw");
            getSharedPreferences(getString(R.string.settings_key), Context.MODE_PRIVATE)
                    .edit()
                    .putString(getString(R.string.setting_acctname), acctName)
                    .putString(getString(R.string.setting_password), newPw)
                    .commit();
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_PASSWORD_RESET));
        } catch (JSONException e) {  // TODO other types of errors
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_STATUS).putExtra(EXTRA_CONNECTION_OK, false));
        }
    }
}
