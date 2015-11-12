package net.tbmcv.tbmmovel;

import android.app.IntentService;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.csipsimple.api.SipProfile;
import com.csipsimple.api.SipProfileState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.R;

import java.io.IOException;

public class AcctDataService extends IntentService {
    public static final String ACTION_RESET_PASSWORD = "net.tbmcv.tbmmovel.RESET_PASSWORD";
    public static final String ACTION_GET_CREDIT = "net.tbmcv.tbmmovel.GET_CREDIT";
    public static final String ACTION_CONFIGURE_LINE = "net.tbmcv.tbmmovel.CONFIGURE_LINE";
    public static final String ACTION_STATUS = "net.tbmcv.tbmmovel.STATUS";
    public static final String ACTION_PASSWORD_RESET = "net.tbmcv.tbmmovel.PASSWORD_RESET";
    public static final String ACTION_ENSURE_LINE = "net.tbmcv.tbmmovel.ENSURE_LINE";
    public static final String EXTRA_ACCT_NAME = "net.tbmcv.tbmmovel.ACCT_NAME";
    public static final String EXTRA_LINE_NAME = "net.tbmcv.tbmmovel.LINE_NAME";
    public static final String EXTRA_PASSWORD = "net.tbmcv.tbmmovel.PASSWORD";
    public static final String EXTRA_CONNECTION_OK = "net.tbmcv.tbmmovel.CONNECTION_OK";
    public static final String EXTRA_PASSWORD_OK = "net.tbmcv.tbmmovel.PASSWORD_OK";
    public static final String EXTRA_CREDIT = "net.tbmcv.tbmmovel.CREDIT";

    static final String LOG_TAG = "AcctDataService";

    public AcctDataService() {
        super("AcctDataService");
    }

    protected JsonRestClient getRestClient() {
        return TbmApi.getInstance(this).getRestClient();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_GET_CREDIT:
                onCommandGetCredit();
                break;
            case ACTION_ENSURE_LINE:
                onCommandEnsureLine();
                break;
            case ACTION_RESET_PASSWORD:
                onCommandResetPassword(
                        intent.getStringExtra(EXTRA_ACCT_NAME),
                        intent.getStringExtra(EXTRA_PASSWORD));
                break;
            case ACTION_CONFIGURE_LINE:
                if (intent.hasExtra(EXTRA_PASSWORD)) {
                    onCommandConfigureLine(
                            intent.getStringExtra(EXTRA_LINE_NAME),
                            intent.getStringExtra(EXTRA_PASSWORD));
                } else {
                    onCommandConfigureLine();
                }
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
                getString(R.string.tbm_settings_key), Context.MODE_PRIVATE);
        String acctName = config.getString(getString(R.string.tbm_setting_acctname), null);
        String password = config.getString(getString(R.string.tbm_setting_password), null);
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
                    .fetch();
            status.putExtra(EXTRA_CREDIT, result.getInt("saldo"));
        } catch (JSONException|IOException e) {
            Log.e(LOG_TAG, "Error fetching saldo", e);
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
                    .fetch();
            String newPw = result.getString("pw");
            getSharedPreferences(getString(R.string.tbm_settings_key), Context.MODE_PRIVATE)
                    .edit()
                    .putString(getString(R.string.tbm_setting_acctname), acctName)
                    .putString(getString(R.string.tbm_setting_password), newPw)
                    .commit();
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_PASSWORD_RESET));
        } catch (JSONException|IOException e) {
            Log.e(LOG_TAG, "Error resetting account password", e);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_STATUS).putExtra(EXTRA_CONNECTION_OK, false));
        }
    }

    private AuthPair resetLinePassword(AuthPair acct) throws JSONException, IOException {
        JSONObject result = getRestClient().buildRequest()
                .auth(acct.name, acct.password)
                .toUri("idens/")
                .toUri(acct.name + "/")
                .toUri("lines/")
                .fetch();
        JsonRestClient.RequestBuilder requestBuilder = getRestClient().buildRequest()
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
                    .fetch();
            return new AuthPair(name, result.getString("pw"));
        } else {
            result = requestBuilder.fetch();
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
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_CONFIGURE_LINE)
                            .putExtra(EXTRA_LINE_NAME, lineAuth.name)
                            .putExtra(EXTRA_PASSWORD, lineAuth.password));
        } catch (JSONException|IOException e) {
            Log.e(LOG_TAG, "Error reconfiguring line", e);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_STATUS).putExtra(EXTRA_CONNECTION_OK, false));
        }
    }

    private static final String[] SELECTION_FIELD_ID = {SipProfile.FIELD_ID};

    private void onCommandConfigureLine(String username, String password) {
        final String realm = getString(R.string.tbm_sip_realm);
        final String displayName = getString(R.string.tbm_csipsimple_display_name);
        final Cursor cursor = getContentResolver().query(
                SipProfile.ACCOUNT_URI, SELECTION_FIELD_ID,
                SipProfile.FIELD_DISPLAY_NAME + "=?", new String[]{displayName}, null);
        if (cursor == null) {
            return;  // TODO
        }
        try {
            final ContentValues values = new ContentValues();
            values.put(SipProfile.FIELD_ACC_ID, "sip:" + username + '@' + realm);
            values.put(SipProfile.FIELD_REG_URI, "sip:" + realm);
            values.put(SipProfile.FIELD_ACTIVE, 1);
            values.put(SipProfile.FIELD_REALM, realm);
            values.put(SipProfile.FIELD_SCHEME, SipProfile.CRED_SCHEME_DIGEST);
            values.put(SipProfile.FIELD_USERNAME, username);
            values.put(SipProfile.FIELD_DATATYPE, SipProfile.CRED_DATA_PLAIN_PASSWD);
            values.put(SipProfile.FIELD_DATA, password);
            if (cursor.moveToFirst()) {
                getContentResolver().update(
                        ContentUris.withAppendedId(SipProfile.ACCOUNT_URI, cursor.getLong(0)),
                        values, null, null);
            } else {
                values.put(SipProfile.FIELD_DISPLAY_NAME, displayName);
                getContentResolver().insert(SipProfile.ACCOUNT_URI, values);
            }
        } finally {
            cursor.close();
        }
    }

    private static final String[] SELECTION_LINE_STATUS = {
            SipProfileState.ACCOUNT_ID,
            SipProfileState.ACTIVE,
            SipProfileState.EXPIRES,
    };

    private static final String[] SELECTION_LINE_CREDS = {
            SipProfile.FIELD_USERNAME,
            SipProfile.FIELD_DATATYPE,
            SipProfile.FIELD_DATA,
    };

    private boolean shouldReconfigure() {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            return false;
        }
        final String displayName = getString(R.string.tbm_csipsimple_display_name);
        Cursor cursor = getContentResolver().query(
                SipProfile.ACCOUNT_STATUS_URI, SELECTION_LINE_STATUS,
                SipProfile.FIELD_DISPLAY_NAME + "=?", new String[]{displayName}, null);
        if (cursor == null) {
            return false;
        }
        final int lineId;
        try {
            if (!cursor.moveToFirst()) {
                return true;  // no such line exists
            } else if (cursor.getInt(1) == 0 || cursor.getInt(2) > 0) {
                return false;  // line disactivated or up
            } else {
                lineId = cursor.getInt(0);
            }
        } finally {
            cursor.close();
        }
        final String lineName;
        final String linePw;
        cursor = getContentResolver().query(
                SipProfile.ACCOUNT_URI, SELECTION_LINE_CREDS,
                SipProfile.FIELD_ID + "=?", new String[]{Integer.toString(lineId)}, null);
        if (cursor == null) {
            return false;
        }
        try {
            if (!cursor.moveToFirst()) {
                return true;
            } else if (cursor.getInt(1) != SipProfile.CRED_DATA_PLAIN_PASSWD) {
                return true;  // TODO
            }
            lineName = cursor.getString(0);
            linePw = cursor.getString(2);
        } finally {
            cursor.close();
        }
        if (lineName == null || linePw == null) {
            return true;
        }
        try {
            JSONObject result = getRestClient().buildRequest()
                    .auth(acct.name, acct.password)
                    .toUri("idens/")
                    .toUri(acct.name + "/")
                    .toUri("lines/")
                    .toUri(lineName + "/")
                    .toUri("pw")
                    .fetch();
            return !linePw.equals(result.getString("pw"));
        } catch (JSONException|IOException e) {
            Log.e(LOG_TAG, "Error retrieving line password", e);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_STATUS).putExtra(EXTRA_CONNECTION_OK, false));
            return false;
        }
    }

    private void onCommandEnsureLine() {
        if (shouldReconfigure()) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_CONFIGURE_LINE));
        }
    }
}
