package net.tbmcv.tbmmovel;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AcctDataService extends IntentService {
    public static final String ACTION_RESET_PASSWORD = "net.tbmcv.tbmmovel.RESET_PASSWORD";
    public static final String ACTION_GET_CREDIT = "net.tbmcv.tbmmovel.GET_CREDIT";
    public static final String ACTION_CONFIGURE_LINE = "net.tbmcv.tbmmovel.CONFIGURE_LINE";
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
            case ACTION_CONFIGURE_LINE:
                onCommandConfigureLine();
                break;
        }
    }

    private static class AuthPair {
        final String name, password;

        AuthPair(@NonNull String name, @NonNull String password) {
            this.name = name;
            this.password = password;
        }
    }

    private AuthPair getAcctAuth() {
        SharedPreferences config = getSharedPreferences(
                getString(R.string.settings_key), Context.MODE_PRIVATE);
        String acctName = config.getString(getString(R.string.setting_acctname), null);
        String password = config.getString(getString(R.string.setting_password), null);
        if (acctName == null || password == null) {
            startActivity(new Intent(this, InitConfigActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        }
        return new AuthPair(acctName, password);
    }

    private void onCommandGetCredit() {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            return;
        }
        Intent status = new Intent(ACTION_STATUS);
        try {
            JSONObject result = getRestClient().buildRequest()
                    .auth(acct.name, acct.password)
                    .toUri("idens/")
                    .toUri(acct.name + "/")
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

    private AuthPair resetLinePassword(AuthPair acct) throws JSONException {
        JSONObject result = getRestClient().buildRequest()
                .auth(acct.name, acct.password)
                .toUri("idens/")
                .toUri(acct.name + "/")
                .toUri("lines/")
                .build()
                .fetch();
        JsonRestClient.Request.Builder requestBuilder = getRestClient().buildRequest()
                .auth(acct.name, acct.password)
                .toUri("idens/")
                .toUri(acct.name + "/")
                .toUri("lines/")
                .method("POST")
                .body(new JSONObject());
        JSONArray lines = result.getJSONArray("lines");
        if (lines.length() > 0) {
            String name = lines.getJSONObject(0).getString("name");
            result = requestBuilder
                    .toUri(name + "/")
                    .toUri("pw")
                    .build()
                    .fetch();
            return new AuthPair(name, result.getString("pw"));
        } else {
            result = requestBuilder.build().fetch();
            return new AuthPair(result.getString("name"), result.getString("pw"));
        }
    }

    private void onCommandConfigureLine() {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            return;
        }
        try {
            AuthPair lineAuth = resetLinePassword(acct);
            // TODO configure line
        } catch (JSONException e) {  // TODO other types of errors
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_STATUS).putExtra(EXTRA_CONNECTION_OK, false));
        }
    }
}
