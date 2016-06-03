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
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

public class LocalServiceConnection<S extends Service> implements ServiceConnection {
    private S service;

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        this.service = ((LocalServiceBinder<S>) service).getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    public S getService() {
        return service;
    }

    public void unbind(Context context) {
        if (service != null) {
            context.unbindService(this);
        }
    }
}
