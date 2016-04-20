package net.tbmcv.tbmmovel;
/*
LocalBroadcastReceiverManager.java
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
