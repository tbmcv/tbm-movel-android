package net.tbmcv.tbmmovel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class LocalBroadcastReceiverManager {
    private final Set<BroadcastReceiver> broadcastReceivers = new CopyOnWriteArraySet<>();
    private final Context context;

    public LocalBroadcastReceiverManager(Context context) {
        this.context = context;
    }

    public void registerReceiver(BroadcastReceiver receiver, @NonNull IntentFilter filter) {
        broadcastReceivers.add(receiver);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver, filter);
    }

    public void unregisterReceiver(BroadcastReceiver receiver) {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
        broadcastReceivers.remove(receiver);
    }

    public void unregisterAll() {
        for (BroadcastReceiver receiver : broadcastReceivers) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
        }
        broadcastReceivers.clear();
    }

    public Context getContext() {
        return context;
    }
}
