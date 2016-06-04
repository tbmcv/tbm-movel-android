package net.tbmcv.tbmmovel;
/*
LocalServiceConnection.java
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
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.Collection;
import java.util.EventListener;
import java.util.concurrent.CopyOnWriteArraySet;

public class LocalServiceConnection<S extends Service> implements ServiceConnection {
    private final Collection<Listener<? super S>> listeners = new CopyOnWriteArraySet<>();
    private S service;

    public interface Listener<S extends Service> extends EventListener {
        void serviceConnected(S service);
        void serviceDisconnected();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        setService(((LocalServiceBinder<S>) service).getService());
        for (Listener<? super S> listener : listeners) {
            listener.serviceConnected(this.service);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        setService(null);
        for (Listener<? super S> listener : listeners) {
            listener.serviceDisconnected();
        }
    }

    protected synchronized void setService(S service) {
        this.service = service;
    }

    public synchronized S getService() {
        return service;
    }

    public boolean isBound() {
        return getService() != null;
    }

    public void unbind(Context context) {
        if (isBound()) {
            context.unbindService(this);
        }
    }

    public void addListener(Listener<? super S> listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener<? super S> listener) {
        listeners.remove(listener);
    }
}
