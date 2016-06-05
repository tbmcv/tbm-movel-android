package net.tbmcv.tbmmovel;
/*
LocalServiceBinder.java
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
import android.os.Binder;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Implementation of IBinder for local bound services.
 *
 * Can be created in a "not yet ready" state, and the reference to the service received via a
 * callback only after the state changes to "ready".
 */
public class LocalServiceBinder<S extends Service> extends Binder {
    private final S service;
    private Collection<LocalServiceListener<? super S>> listeners;
    private boolean ready;

    public LocalServiceBinder(S service, boolean ready) {
        this.service = service;
        this.ready = ready;
        if (!ready) {
            listeners = new ArrayList<>();
        }
    }

    public LocalServiceBinder(S service) {
        this(service, true);
    }

    public S getService() {
        return service;
    }

    public synchronized boolean isReady() {
        return ready;
    }

    public void connectWhenReady(LocalServiceListener<? super S> listener) {
        boolean ready;
        synchronized (this) {
            ready = isReady();
            if (!ready) {
                listeners.add(listener);
            }
        }
        if (ready) {
            listener.serviceConnected(getService());
        }
    }

    public void setReady() {
        Collection<LocalServiceListener<? super S>> listeners;
        synchronized (this) {
            if (ready) {
                return;
            }
            ready = true;
            listeners = this.listeners;
            this.listeners = null;
        }
        for (LocalServiceListener<? super S> listener : listeners) {
            listener.serviceConnected(service);
        }
    }
}
