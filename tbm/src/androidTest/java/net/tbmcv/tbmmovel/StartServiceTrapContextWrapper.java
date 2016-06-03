package net.tbmcv.tbmmovel;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StartServiceTrapContextWrapper extends ContextWrapper {
    private static final IBinder REALLY_BIND = new Binder();
    private final List<Intent> startIntents = new ArrayList<>();
    private final Map<ComponentName, IBinder> bindable = new HashMap<>();
    private final List<Intent> bindIntents = new ArrayList<>();
    private final Set<ServiceConnection> boundConnections = new HashSet<>();

    public StartServiceTrapContextWrapper(Context base) {
        super(base);
    }

    @Override
    public ComponentName startService(Intent service) {
        startIntents.add(service);
        return service.getComponent();
    }

    public Intent getServiceStarted(Class<? extends Service> cls) {
        return getServiceStarted(cls, null);
    }

    public Intent getServiceStarted(Class<? extends Service> cls, String action) {
        ComponentName name = getComponentName(cls);
        for (Intent intent : startIntents) {
            if (name.equals(intent.getComponent())
                    && (action == null || action.equals(intent.getAction()))) {
                return intent;
            }
        }
        return null;
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        bindIntents.add(service);
        IBinder binder = bindable.get(service.getComponent());
        if (binder == REALLY_BIND) {
            return super.bindService(service, conn, flags);
        } else if (binder != null) {
            conn.onServiceConnected(service.getComponent(), binder);
            boundConnections.add(conn);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        if (!boundConnections.remove(conn)) {
            super.unbindService(conn);
        }
    }

    protected Intent getBindServiceIntent(Class<? extends Service> cls) {
        ComponentName name = getComponentName(cls);
        for (Intent intent : bindIntents) {
            if (name.equals(intent.getComponent())) {
                return intent;
            }
        }
        return null;
    }

    protected ComponentName getComponentName(Class<?> cls) {
        return new ComponentName(getBaseContext(), cls);
    }

    protected void removeBoundService(Class<? extends Service> cls) {
        bindable.remove(getComponentName(cls));
    }

    protected void setBoundService(Class<? extends Service> cls, IBinder binder) {
        bindable.put(getComponentName(cls), binder);
    }

    protected void allowBoundService(Class<? extends Service> cls) {
        setBoundService(cls, REALLY_BIND);
    }
}
