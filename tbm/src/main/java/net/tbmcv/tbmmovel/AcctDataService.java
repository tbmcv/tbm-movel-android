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
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.core.LinphoneCoreException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Bound local service that handles querying the account via the REST server.
 *
 * Automatically checks and (re)configures the voip line.
 */
public class AcctDataService extends Service {
    public static final String ACTION_ACCT_CHANGED = "net.tbmcv.tbmmovel.ACCT_CHANGED";

    static final String LOG_TAG = "AcctDataService";

    @IntDef({NOTHING_REQUESTED, REQUESTED_CHECK, REQUESTED_RESET, SHUTTING_DOWN})
    @Retention(RetentionPolicy.SOURCE)
    protected @interface RequestedAction { }

    protected static final int NOTHING_REQUESTED = 0;
    protected static final int REQUESTED_CHECK = 1;
    protected static final int REQUESTED_RESET = 2;
    protected static final int SHUTTING_DOWN = 3;

    static Pauser pauser = new RealPauser();

    @RequestedAction
    protected int state = NOTHING_REQUESTED;

    private Thread lineResetThread;

    private final LocalServiceConnection<TbmApiService> tbmApiConnection =
            new LocalServiceConnection<>();

    public static class Binder extends LocalServiceBinder<AcctDataService> {
        public Binder(AcctDataService service) {
            super(service, false);
        }
    }

    private final Binder binder = new Binder(this);

    /**
     * Creates a new RestRequest that checks for 401 errors.
     *
     * On a 401 error, the local credentials are erased and the initial config screen is shown.
     * @param acct current account credentials from {@link #getAcctAuth()}
     * @return the new RestRequest
     */
    @NonNull
    public RestRequest createRequest(@NonNull AuthPair acct) {
        RestRequest request = tbmApiConnection.getService().createRequest();
        request.setAuth(acct.name, acct.password);
        final RestRequest.Fetcher oldFetcher = request.getFetcher();
        request.setFetcher(new RestRequest.Fetcher() {
            @Override
            public String fetch(@NonNull RestRequest.Connection connection) throws IOException {
                try {
                    return oldFetcher.fetch(connection);
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

    protected static class ShuttingDown extends Exception { }

    /**
     * Request that the voip line configuration be checked and reset if appropriate.
     *
     * Returns immediately.
     */
    public synchronized void checkLine() {
        if (state < REQUESTED_CHECK) {
            state = REQUESTED_CHECK;
            notifyAll();
        }
    }

    /**
     * Request that the voip line configuration be reset.
     *
     * Returns immediately.
     */
    public synchronized void resetLine() {
        if (state < REQUESTED_RESET) {
            state = REQUESTED_RESET;
            notifyAndInterrupt();
        }
    }

    protected synchronized void shutdownLineLoop() {
        state = SHUTTING_DOWN;
        notifyAndInterrupt();
    }

    /**
     * Clear requests if not shutting down, otherwise throw ShuttingDown
     * @throws ShuttingDown
     */
    protected synchronized void onResetFinished() throws ShuttingDown {
        if (state == SHUTTING_DOWN) {
            throw new ShuttingDown();
        }
        state = NOTHING_REQUESTED;
        notifyAll();
    }

    /**
     * Notifies waiters that our state has changed, interrupting the line reset thread.
     *
     * Use for state changes that should stop current network requests, such as from
     * REQUESTED_CHECK to SHUTTING_DOWN.
     *
     * Does not interrupt the line reset thread if called from it.
     */
    protected synchronized void notifyAndInterrupt() {
        if (Thread.currentThread() != lineResetThread) {
            lineResetThread.interrupt();
        }
        notifyAll();
    }

    protected void notifyAcctAuthChange() {
        notifyAndInterrupt();
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_ACCT_CHANGED));
    }

    protected void lineConfigurationLoop() throws ShuttingDown {
        //noinspection InfiniteLoopStatement
        while (true) {
            AuthPair acct = waitUntilReconfigureNeeded();
            // state should be REQUESTED_RESET
            try {
                configureLine(acct);
            } catch (InterruptedException | InterruptedIOException e) {
                /* continue */
            } catch (LinphoneCoreException | IOException | JSONException e) {
                Log.e(LOG_TAG, "Error configuring line", e);
            }
            onResetFinished();
        }
    }

    @Override
    public void onCreate() {
        lineResetThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lineConfigurationLoop();
                } catch (ShuttingDown e) {
                    /* just exit */
                }
            }
        });
        tbmApiConnection.addListener(new LocalServiceListener<TbmApiService>() {
            @Override
            public void serviceConnected(TbmApiService service) {
                Log.d(LOG_TAG, "TbmApiService ready; so we're ready");
                binder.setReady();
                lineResetThread.start();
            }

            @Override
            public void serviceDisconnected() {
                Log.w(LOG_TAG, "TbmApiService disappeared!");
            }
        });
        tbmApiConnection.bind(this, TbmApiService.class);
        super.onCreate();
        checkLine();
        Log.d(LOG_TAG, "onCreate() finished");
    }

    @Override
    public void onDestroy() {
        shutdownLineLoop();
        tbmApiConnection.unbind(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Gets the current account credentials stored on the local device
     * @return the credentials as an AuthPair
     */
    @Nullable
    public synchronized AuthPair getAcctAuth() {
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

    private synchronized void resetAuthConfig() {
        getSharedPreferences(getString(R.string.tbm_settings_key), Context.MODE_PRIVATE)
                .edit()
                .remove(getString(R.string.tbm_setting_acctname))
                .remove(getString(R.string.tbm_setting_password))
                .commit();
        notifyAcctAuthChange();
        try {
            TbmLinphoneConfigurator.getInstance().clearLineConfig();
        } catch (LinphoneCoreException e) {
            Log.e(LOG_TAG, "Error clearing line config", e);
        }
        startActivity(new Intent(this, InitConfigActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private synchronized void setAuthConfig(@NonNull String acctName, @NonNull String password) {
        getSharedPreferences(getString(R.string.tbm_settings_key), Context.MODE_PRIVATE)
                .edit()
                .putString(getString(R.string.tbm_setting_acctname), acctName)
                .putString(getString(R.string.tbm_setting_password), password)
                .commit();
        notifyAcctAuthChange();
    }

    /**
     * Gets the account's current amount of credit
     *
     * This is a synchronous method which sends a network request and waits for its response.
     * @return the amount of credit
     * @throws IOException
     * @throws JSONException
     */
    public int getCredit() throws IOException, JSONException {
        AuthPair acct = getAcctAuth();
        if (acct == null) {
            throw new HttpError(401);
        }
        RestRequest request = createRequest(acct);
        request.toUri("idens/" + acct.name + "/saldo/");
        return request.fetchJson().getInt("saldo");
    }

    /**
     * Resets the account password.
     *
     * This is a synchronous method which sends a network request and waits for its response.
     * @param acctName name of account (including initial group, such as "c/")
     * @param password current account password
     * @return false if wrong username or password
     * @throws JSONException
     * @throws IOException
     */
    public boolean resetPassword(@NonNull String acctName, @NonNull String password)
            throws JSONException, IOException {
        byte[] newPwBinary = new byte[12];
        new SecureRandom().nextBytes(newPwBinary);
        String newPw = Base64.encodeToString(newPwBinary,
                Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
        try {
            RestRequest request = tbmApiConnection.getService().createRequest();
            request.setAuth(acctName, password);
            request.toUri("idens/" + acctName + "/pw");
            request.setMethod("PUT");
            request.setBody(new JSONObject().put("value", newPw));
            request.fetch();
            setAuthConfig(acctName, newPw);
            return true;
        } catch (HttpError e) {
            if (e.getResponseCode() == 401) {
                return false;
            }
            throw e;
        }
    }

    @NonNull
    private AuthPair resetLinePassword(@NonNull AuthPair acct) throws JSONException, IOException {
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

    private void configureLine(@NonNull AuthPair acct)
            throws LinphoneCoreException, InterruptedException, IOException, JSONException {
        AuthPair lineAuth = resetLinePassword(acct);
        pauser.pause(3, TimeUnit.SECONDS);  // TODO configure somewhere
        TbmLinphoneConfigurator.getInstance().configureLine(
                getString(R.string.tbm_sip_realm), lineAuth.name, lineAuth.password);
    }

    @NonNull
    public static String createHa1(@NonNull String username, @NonNull String password,
                                   @NonNull String realm) {
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

    private String createHa1(@NonNull String username, @NonNull String password) {
        return createHa1(username, password, getString(R.string.tbm_sip_realm));
    }

    /**
     * Loops and waits, monitoring state, until we should reconfigure the voip line.
     * @return the current stored account
     * @throws ShuttingDown
     */
    @NonNull
    private AuthPair waitUntilReconfigureNeeded() throws ShuttingDown {
        boolean lineWasCorrect = false;
        // Double while loop lets us stay in synchronized block as much as possible, but
        // check the REST server outside of it. Outer loop is only left by return or exception.
        while (true) {
            AuthPair acct, existing;
            synchronized (this) {
                while (true) {
                    if (state == SHUTTING_DOWN) {
                        throw new ShuttingDown();
                    } else if (lineWasCorrect && state == REQUESTED_CHECK) {
                        state = NOTHING_REQUESTED;
                        lineWasCorrect = false;
                    } else if (state >= REQUESTED_CHECK) {
                        acct = getAcctAuth();
                        if (acct == null) {
                            Log.d(LOG_TAG, "Can't reconfigure line, because no saved account");
                        } else if (state == REQUESTED_RESET) {
                            Log.d(LOG_TAG, "Reset explicitly requested");
                            return acct;
                        } else {
                            try {
                                existing = TbmLinphoneConfigurator.getInstance().getLineConfig();
                                if (existing == null) {
                                    Log.d(LOG_TAG, "Should reconfigure because local line misconfigured");
                                    resetLine();
                                    return acct;
                                }
                                // Ready to check the server's line password. We need to break out
                                // of the synchronized block.
                                break;
                            } catch (LinphoneCoreException e) {
                                Log.e(LOG_TAG, "Can't reconfigure because no LinphoneCore available");
                            }
                        }
                    }
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // Continue loop. If we're really supposed to quit, state will be
                        // SHUTTING_DOWN.
                    }
                }
            }
            // Ready to check the server's line password
            try {
                RestRequest request = createRequest(acct);
                request.toUri("idens/" + acct.name + "/lines/" + existing.name + "/pw");
                String pw = request.fetchJson().getString("pw");
                lineWasCorrect = existing.password.equals(createHa1(existing.name, pw));
                if (!lineWasCorrect) {
                    resetLine();
                    return acct;
                }
            } catch (InterruptedIOException e) {
                /* no need to log */
            } catch (JSONException | IOException e) {
                Log.e(LOG_TAG, "Error checking line password", e);
            }
            // Continue loop. If we're really supposed to quit, state will be
            // SHUTTING_DOWN.
        }
    }
}
