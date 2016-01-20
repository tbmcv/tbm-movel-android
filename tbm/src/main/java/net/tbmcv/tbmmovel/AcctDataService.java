package net.tbmcv.tbmmovel;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

import static org.linphone.core.LinphoneAddress.*;

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
        Log.d(LOG_TAG, "Received Intent: " + intent.getAction());
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
            Log.d(LOG_TAG, "Account not configured; running initial configuration");
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
        byte[] newPwBinary = new byte[12];
        new SecureRandom().nextBytes(newPwBinary);
        String newPw = Base64.encodeToString(newPwBinary,
                Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
        try {
            getSharedPreferences(getString(R.string.tbm_settings_key), Context.MODE_PRIVATE)
                    .edit()
                    .putString(getString(R.string.tbm_setting_acctname), acctName)
                    .putString(getString(R.string.tbm_setting_password), newPw)
                    .commit();
            getRestClient().buildRequest()
                    .auth(acctName, password)
                    .toUri("idens/")
                    .toUri(acctName + "/")
                    .toUri("pw")
                    .method("PUT")
                    .body(new JSONObject().put("value", newPw))
                    .fetch();
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
            Log.d(LOG_TAG, "Resetting voip line " + name);
            return new AuthPair(name, result.getString("pw"));
        } else {
            Log.d(LOG_TAG, "Requesting new voip line");
            result = requestBuilder.fetch();
            return new AuthPair(result.getString("name"), result.getString("pw"));
        }
    }

    private void onCommandConfigureLine() {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            Log.w(LOG_TAG, "Can't configure line because no account saved");
            return;
        }
        try {
            AuthPair lineAuth = resetLinePassword(acct);
            startService(new Intent(this, AcctDataService.class).setAction(ACTION_CONFIGURE_LINE)
                    .putExtra(EXTRA_LINE_NAME, lineAuth.name)
                    .putExtra(EXTRA_PASSWORD, lineAuth.password));
        } catch (JSONException|IOException e) {
            Log.e(LOG_TAG, "Error reconfiguring line", e);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_STATUS).putExtra(EXTRA_CONNECTION_OK, false));
        }
    }

    private void onCommandConfigureLine(String username, String password) {
        final String realm = getString(R.string.tbm_sip_realm);
        try {
            LinphoneCore lc = LinphoneManager.getLc();
            lc.clearAuthInfos();
            lc.clearProxyConfigs();
            LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(
                    "sip:" + realm);
            proxyAddr.setTransport(TransportType.LinphoneTransportTcp);
            LinphoneProxyConfig proxyConfig = lc.createProxyConfig(
                    "sip:" + username + "@" + realm, proxyAddr.asStringUriOnly(), null, true);
            proxyConfig.setExpires(300);  // TODO configure somewhere
            LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(
                    username, null, null, createHa1(username, password, realm), null, realm);
            lc.addProxyConfig(proxyConfig);
            lc.addAuthInfo(authInfo);
            lc.setDefaultProxyConfig(proxyConfig);
        } catch (LinphoneCoreException e) {
            Log.e(LOG_TAG, "Error saving line configuration", e);
        }
    }

    public static String createHa1(String username, String password, String realm) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(username.getBytes("UTF-8"));
            md5.update((byte) ':');
            md5.update(realm.getBytes("UTF-8"));
            md5.update((byte) ':');
            md5.update(password.getBytes("UTF-8"));
            return String.format(Locale.US, "%032x", new BigInteger(1, md5.digest()));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    private String createHa1(String username, String password) {
        return createHa1(username, password, getString(R.string.tbm_sip_realm));
    }

    private boolean shouldReconfigure() {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            Log.d(LOG_TAG, "Can't reconfigure line, because there's no saved account");
            return false;
        }
        LinphoneCore lc = LinphoneManager.getLc();
        LinphoneAuthInfo[] authInfos = lc.getAuthInfosList();
        if (authInfos.length != 1) {
            Log.d(LOG_TAG, "Should reconfigure because of local voip line count");
            return true;
        }
        String lineName = authInfos[0].getUsername();
        String lineHa1 = authInfos[0].getHa1();
        Log.d(LOG_TAG, "Line name: " + lineName + " line HA1: " + lineHa1);
        if (lineHa1 == null || lineName == null) {
            Log.d(LOG_TAG, "Should reconfigure because local voip line misconfigured");
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
            return !lineHa1.equals(createHa1(lineName, result.getString("pw")));
        } catch (JSONException|IOException e) {
            Log.e(LOG_TAG, "Error retrieving line password", e);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(ACTION_STATUS).putExtra(EXTRA_CONNECTION_OK, false));
            return false;
        }
    }

    private void onCommandEnsureLine() {
        if (shouldReconfigure()) {
            startService(new Intent(this, AcctDataService.class).setAction(ACTION_CONFIGURE_LINE));
        }
    }
}
