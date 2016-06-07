package net.tbmcv.tbmmovel;
/*
SaldoService.java
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
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

public class SaldoService extends Service {
    public static final String ACTION_UPDATE = "net.tbmcv.tbmmovel.SALDO_UPDATE";
    public static final String EXTRA_CREDIT = "net.tbmcv.tbmmovel.SALDO";
    public static final int UNKNOWN_CREDIT = Integer.MIN_VALUE;
    static final String LOG_TAG = "SaldoService";

    static Pauser pauser = new RealPauser();

    private final LocalServiceConnection<AcctDataService> acctDataConnection =
            new LocalServiceConnection<>();
    private Thread pollThread;
    private volatile int credit;
    private volatile RestRequest.Connection currentPollConnection;

    /* only used in pollLoop() */
    private String lastETag;

    public static class Binder extends LocalServiceBinder<SaldoService> {
        public Binder(SaldoService service) {
            super(service, false);
        }
    }

    protected final Binder binder = new Binder(this);

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        credit = UNKNOWN_CREDIT;
        acctDataConnection.addListener(new LocalServiceListener<AcctDataService>() {
            @Override
            public void serviceConnected(AcctDataService service) {
                binder.setReady();
                pollThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(LOG_TAG, "Poll thread started");
                        try {
                            pollLoop();
                        } catch (InterruptedException e) {
                            /* ignore */
                        } finally {
                            Log.i(LOG_TAG, "Poll thread finished");
                        }
                    }
                });
                pollThread.start();
            }

            @Override
            public void serviceDisconnected() { }
        });
        acctDataConnection.bind(this, AcctDataService.class);
    }

    protected void pollLoop() throws InterruptedException {
        lastETag = null;
        while (!Thread.interrupted()) {
            AcctDataService service = acctDataConnection.getService();
            if (service == null) {
                break;
            }
            AuthPair acct = service.getAcctAuth();
            if (acct == null) {
                setCredit(UNKNOWN_CREDIT);
                Log.d(LOG_TAG, "Pausing because no account config");
                pauser.pause(15, TimeUnit.SECONDS);   // TODO configure somewhere
                continue;
            }
            try {
                RestRequest request = service.createRequest(acct);
                request.toUri("idens/" + acct.name + "/saldo/");
                if (lastETag != null) {
                    request.setIfNoneMatch(lastETag);
                    request.setWaitChange(300);   // TODO configure somewhere
                    request.setConnectTimeout(300 * 1000 + 5000);
                }

                final RestRequest.Fetcher oldFetcher = request.getFetcher();
                request.setFetcher(new RestRequest.Fetcher() {
                    @Override
                    public String fetch(@NonNull RestRequest.Connection connection) throws IOException {
                        currentPollConnection = connection;
                        String body = oldFetcher.fetch(connection);
                        lastETag = connection.getHeader("etag");
                        return body;
                    }
                });

                JSONObject result;
                try {
                    Log.d(LOG_TAG, "Sending request");
                    result = request.fetchJson();
                } finally {
                    currentPollConnection = null;
                }
                setCredit(result.getInt("saldo"));
                pauser.pause(5, TimeUnit.SECONDS);   // TODO configure somewhere
            } catch (InterruptedIOException e) {
                break;
            } catch (IOException|JSONException e) {
                Log.e(LOG_TAG, "Error getting credit", e);
                lastETag = null;
                pauser.pause(15, TimeUnit.SECONDS);   // TODO configure somewhere
            }
        }
    }

    @Override
    public void onDestroy() {
        pollThread.interrupt();
        RestRequest.Connection connection = currentPollConnection;
        if (connection != null) {
            connection.close();
            currentPollConnection = null;
        }
        acctDataConnection.unbind(this);
    }

    public int getCredit() {
        return credit;
    }

    protected void setCredit(int credit) {
        this.credit = credit;
        Log.d(LOG_TAG, "Credit set to " + (credit == UNKNOWN_CREDIT ? "unknown" : credit));
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent(ACTION_UPDATE).putExtra(EXTRA_CREDIT, credit));
    }
}
