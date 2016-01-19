package net.tbmcv.tbmmovel;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

public class StartServiceTrapContextWrapper extends ContextWrapper {
    private final List<Intent> serviceIntents = new ArrayList<>();

    public StartServiceTrapContextWrapper(Context base) {
        super(base);
    }

    @Override
    public ComponentName startService(Intent service) {
        serviceIntents.add(service);
        return service.getComponent();
    }

    public Intent getServiceStarted(Class<? extends Service> cls) {
        return getServiceStarted(cls, null);
    }

    public Intent getServiceStarted(Class<? extends Service> cls, String action) {
        synchronized (serviceIntents) {
            for (Intent intent : serviceIntents) {
                if (cls.getCanonicalName().equals(intent.getComponent().getClassName())
                        && (action == null || action.equals(intent.getAction()))) {
                    return intent;
                }
            }
        }
        return null;
    }
}
