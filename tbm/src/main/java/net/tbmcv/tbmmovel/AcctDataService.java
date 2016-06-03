package net.tbmcv.tbmmovel;
/*
AcctDataService.java
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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.core.LinphoneCoreException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AcctDataService extends Service {
    static final String LOG_TAG = "AcctDataService";

    private final LocalServiceConnection<TbmApiService> tbmApiConnection =
            new LocalServiceConnection<>();

    public static class Binder extends LocalServiceBinder<AcctDataService> {
        public Binder(AcctDataService service) {
            super(service);
        }
    }

    public RestRequest createRequest(AuthPair acct) throws HttpError {
        RestRequest request = tbmApiConnection.getService().createRequest();
        request.setAuth(acct.name, acct.password);
        final RestRequest.Fetcher oldFetcher = request.getFetcher();
        request.setFetcher(new RestRequest.Fetcher() {
            @Override
            public String fetch(RestRequest request) throws IOException {
                try {
                    return oldFetcher.fetch(request);
                } catch (HttpError e) {
                    if (e.getResponseCode() == 401) {
                        resetAuthConfig();
                    }
                    throw e;
                }
            }
        });
        return request;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bindService(new Intent(this, TbmApiService.class),
                tbmApiConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        tbmApiConnection.unbind(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder(this);
    }

    public AuthPair getAcctAuth() {
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

    private void resetAuthConfig() {
        getSharedPreferences(getString(R.string.tbm_settings_key), Context.MODE_PRIVATE)
                .edit()
                .remove(getString(R.string.tbm_setting_acctname))
                .remove(getString(R.string.tbm_setting_password))
                .commit();
        try {
            TbmLinphoneConfigurator.getInstance().clearLineConfig();
        } catch (LinphoneCoreException e) {
            Log.e(LOG_TAG, "Error clearing line config", e);
        }
        startActivity(new Intent(this, InitConfigActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public int getCredit() throws IOException, JSONException {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            throw new HttpError(401);
        }
        RestRequest request = createRequest(acct);
        request.toUri("idens/" + acct.name + "/saldo/");
        return request.fetchJson().getInt("saldo");
    }

    public boolean resetPassword(String acctName, String password) throws JSONException, IOException {
        byte[] newPwBinary = new byte[12];
        new SecureRandom().nextBytes(newPwBinary);
        String newPw = Base64.encodeToString(newPwBinary,
                Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
        getSharedPreferences(getString(R.string.tbm_settings_key), Context.MODE_PRIVATE)
                .edit()
                .putString(getString(R.string.tbm_setting_acctname), acctName)
                .putString(getString(R.string.tbm_setting_password), newPw)
                .commit();
        try {
            RestRequest request = tbmApiConnection.getService().createRequest();
            request.setAuth(acctName, password);
            request.toUri("idens/" + acctName + "/pw");
            request.setMethod("PUT");
            request.setBody(new JSONObject().put("value", newPw));
            request.fetch();
            return true;
        } catch (HttpError e) {
            if (e.getResponseCode() == 401) {
                return false;
            }
            throw e;
        }
    }

    private AuthPair resetLinePassword(AuthPair acct) throws JSONException, IOException {
        RestRequest request = createRequest(acct);
        request.toUri("idens/" + acct.name + "/lines/");
        RestRequest postRequest = request.clone();
        JSONArray lines = request.fetchJson().getJSONArray("lines");

        postRequest.setMethod("POST");
        postRequest.setBody(new JSONObject());
        String name;
        JSONObject result;
        if (lines.length() > 0) {
            name = lines.getJSONObject(0).getString("name");
            postRequest.toUri(name + "/pw");
            Log.d(LOG_TAG, "Resetting voip line " + name);
            result = postRequest.fetchJson();
        } else {
            Log.d(LOG_TAG, "Requesting new voip line");
            result = postRequest.fetchJson();
            name = result.getString("name");
        }
        return new AuthPair(name, result.getString("pw"));
    }

    public void configureLine()
            throws LinphoneCoreException, InterruptedException, IOException, JSONException {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            Log.w(LOG_TAG, "Can't configure line because no account saved");
            return;
        }
        AuthPair lineAuth = resetLinePassword(acct);
        pauser.pause(3, TimeUnit.SECONDS);  // TODO configure somewhere
        TbmLinphoneConfigurator.getInstance().configureLine(
                getString(R.string.tbm_sip_realm), lineAuth.name, lineAuth.password);
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
            throw new RuntimeException("Can't create HA1", e);
        }
    }

    private String createHa1(String username, String password) {
        return createHa1(username, password, getString(R.string.tbm_sip_realm));
    }

    private boolean shouldReconfigure() throws IOException, JSONException {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            Log.d(LOG_TAG, "Can't reconfigure line, because there's no saved account");
            return false;
        }
        AuthPair existing;
        try {
            existing = TbmLinphoneConfigurator.getInstance().getLineConfig();
        } catch (LinphoneCoreException e) {
            Log.e(LOG_TAG, "Can't reconfigure because no LinphoneCore available");
            return false;
        }
        if (existing == null) {
            Log.d(LOG_TAG, "Should reconfigure because local voip line misconfigured");
            return true;
        }
        RestRequest request = createRequest(acct);
        request.toUri("idens/" + acct.name + "/lines/" + existing.name + "/pw");
        String pw = request.fetchJson().getString("pw");
        return !existing.password.equals(createHa1(existing.name, pw));
    }

    public void ensureLine()
            throws IOException, JSONException, LinphoneCoreException, InterruptedException {
        if (shouldReconfigure()) {
            configureLine();
        }
    }

    interface Pauser {
        void pause(long timeout, TimeUnit unit) throws InterruptedException;
    }

    static Pauser pauser = new RealPauser();

    static class RealPauser implements Pauser {
        @Override
        public void pause(long duration, TimeUnit unit) throws InterruptedException {
            long nanos = unit.toNanos(duration);
            Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000));
        }
    }
}
