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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

public class SaldoService extends Service {
    public static final String ACTION_UPDATE = "net.tbmcv.tbmmovel.SALDO_UPDATE";
    public static final String EXTRA_CREDIT = "net.tbmcv.tbmmovel.SALDO";
    public static final int UNKNOWN_CREDIT = Integer.MIN_VALUE;
    static final String LOG_TAG = "SaldoService";

    static Pauser pauser = new RealPauser();

    private final LocalServiceConnection<AcctDataService> acctDataConnection =
            new LocalServiceConnection<>();
    private BroadcastReceiver broadcastReceiver;
    private Thread pollThread;
    private volatile int credit;

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
        pollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "Poll thread started");
                try {
                    pollLoop();
                } catch (ShuttingDown e) {
                    /* fall through */
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Unexpected exception in poll thread", e);
                } finally {
                    Log.i(LOG_TAG, "Poll thread finished");
                }
            }
        });
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                interruptPollLoop();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(
                broadcastReceiver, new IntentFilter(AcctDataService.ACTION_ACCT_CHANGED));
        acctDataConnection.addListener(new LocalServiceListener<AcctDataService>() {
            @Override
            public void serviceConnected(AcctDataService service) {
                binder.setReady();
                pollThread.start();
            }

            @Override
            public void serviceDisconnected() {
                Log.w(LOG_TAG, "AcctDataService disappeared!");
            }
        });
        acctDataConnection.bind(this, AcctDataService.class);
    }

    private boolean pollLoopNotified, shuttingDown;

    protected static class ShuttingDown extends Exception { }

    protected synchronized void notifyPollLoop() {
        pollLoopNotified = true;
    }

    protected synchronized void interruptPollLoop() {
        pollLoopNotified = true;
        pollThread.interrupt();
    }

    protected synchronized void shutdownPollLoop() {
        shuttingDown = true;
        pollThread.interrupt();
    }

    protected synchronized void waitForPollLoopNotify() throws ShuttingDown {
        checkShuttingDown();
        while (!pollLoopNotified) {
            try {
                wait();
            } catch (InterruptedException e) {
                checkShuttingDown();
            }
        }
        Log.d(LOG_TAG, "Poll loop woken up");
        pollLoopNotified = false;
    }

    protected synchronized void clearPollLoopNotify() throws ShuttingDown {
        pollLoopNotified = false;
    }

    protected synchronized void checkShuttingDown() throws ShuttingDown {
        if (shuttingDown) {
            throw new ShuttingDown();
        }
    }

    protected void pollLoop() throws ShuttingDown {
        lastETag = null;
        while (true) {
            checkShuttingDown();
            AcctDataService service = acctDataConnection.getService();
            if (service == null) {
                Log.w(LOG_TAG, "No AcctDataService!");
                break;
            }
            AuthPair acct = service.getAcctAuth();
            if (acct == null) {
                setCredit(UNKNOWN_CREDIT);
                Log.d(LOG_TAG, "Waiting because no account config");
                waitForPollLoopNotify();
                continue;
            } else {
                clearPollLoopNotify();
            }
            int pauseSeconds;
            try {
                RestRequest request = service.createRequest(acct);
                request.toUri("idens/" + acct.name + "/saldo/");
                if (lastETag != null) {
                    request.setIfNoneMatch(lastETag);
                    request.setWaitChange(300);   // TODO configure somewhere
                    request.setConnectTimeout((300 + 5) * 1000);
                    request.setReadTimeout((300 + 10) * 1000);
                }

                final RestRequest.Fetcher oldFetcher = request.getFetcher();
                request.setFetcher(new RestRequest.Fetcher() {
                    @Override
                    public String fetch(@NonNull RestRequest.Connection connection) throws IOException {
                        String body = oldFetcher.fetch(connection);
                        lastETag = connection.getHeader("etag");
                        return body;
                    }
                });

                Log.d(LOG_TAG, "Sending request");
                setCredit(request.fetchJson().getInt("saldo"));
                pauseSeconds = 5;   // TODO configure somewhere
            } catch (SocketTimeoutException e) {
                Log.w(LOG_TAG, "Socket timed out", e);
                pauseSeconds = 15;   // TODO configure somewhere
            } catch (InterruptedIOException e) {
                Log.d(LOG_TAG, "Poll fetch interrupted");
                pauseSeconds = 0;
            } catch (IOException|JSONException e) {
                Log.e(LOG_TAG, "Error getting credit", e);
                lastETag = null;
                pauseSeconds = 15;   // TODO configure somewhere
            }
            if (pauseSeconds > 0) {
                try {
                    Log.d(LOG_TAG, "Pausing for " + pauseSeconds + " seconds");
                    pauser.pause(pauseSeconds, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Log.d(LOG_TAG, "Poll loop pause interrupted");
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        shutdownPollLoop();
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
