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
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Convenience class for connecting to local bound services that use LocalServiceBinder.
 *
 * Instead of passing a ServiceConnection to bindService() and unbindService(),
 * call bind() and unbind(), and add a listener or call isBound() to find out when the service
 * is ready.
 *
 * Unbind is idempotent, so it can be called whenever the service is not needed.
 */
public class LocalServiceConnection<S extends Service> implements LocalServiceListener<S> {
    private final Collection<LocalServiceListener<? super S>> listeners = new CopyOnWriteArraySet<>();
    private S service;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ((LocalServiceBinder<? extends S>) service).connectWhenReady(LocalServiceConnection.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceDisconnected();
        }
    };

    @Override
    public void serviceConnected(S service) {
        setService(service);
        for (LocalServiceListener<? super S> listener : listeners) {
            listener.serviceConnected(service);
        }
    }

    @Override
    public void serviceDisconnected() {
        synchronized (this) {
            if (!isBound()) {
                return;
            }
            setService(null);
        }
        for (LocalServiceListener<? super S> listener : listeners) {
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

    public void bind(Context context, Intent intent, int flags) {
        context.bindService(intent, connection, flags);
    }

    public void bind(Context context, Intent intent) {
        bind(context, intent, Service.BIND_AUTO_CREATE);
    }

    public void bind(Context context, Class<S> cls) {
        bind(context, new Intent(context, cls), Service.BIND_AUTO_CREATE);
    }

    public void unbind(Context context) {
        if (isBound()) {
            context.unbindService(connection);
        }
    }

    public void addListener(LocalServiceListener<? super S> listener) {
        listeners.add(listener);
    }

    public void removeListener(LocalServiceListener<? super S> listener) {
        listeners.remove(listener);
    }
}
