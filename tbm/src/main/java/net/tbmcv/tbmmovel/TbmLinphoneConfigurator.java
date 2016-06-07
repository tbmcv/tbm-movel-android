package net.tbmcv.tbmmovel;
/*
TbmLinphoneConfigurator.java
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;

public class TbmLinphoneConfigurator {
    static TbmLinphoneConfigurator instance = new TbmLinphoneConfigurator();

    public static TbmLinphoneConfigurator getInstance() {
        return instance;
    }

    public void setDefaultSettings() throws LinphoneCoreException {
        setDefaultNetworkSettings();
        setDefaultCodecs();
    }

    public void setDefaultCodecs() throws LinphoneCoreException {
        LinphoneCore lc = getLinphoneCore();
        lc.enableAdaptiveRateControl(false);
        lc.enableKeepAlive(true);
        for (PayloadType codec : lc.getAudioCodecs()) {
            lc.enablePayloadType(codec, codecDefaultEnabled(codec));
        }
    }

    public void setDefaultNetworkSettings() throws LinphoneCoreException {
        LinphoneCore lc = getLinphoneCore();
        lc.enableKeepAlive(true);
        lc.enableDnsSrv(false);
        lc.setStunServer(null);
    }

    public void startEchoCalibration(@NonNull LinphoneCoreListener listener)
            throws LinphoneCoreException {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null && lc.needsEchoCalibration()) {
            lc.startEchoCalibration(listener);
        }
    }

    public boolean codecDefaultEnabled(PayloadType codec) {
        return codec.getRate() == 8000 && "GSM".equals(codec.getMime());
    }

    public void configureLine(@NonNull String realm, @NonNull String username, @NonNull String password)
            throws LinphoneCoreException {
        LinphoneCore lc = getLinphoneCore();
        lc.clearAuthInfos();
        lc.clearProxyConfigs();
        LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(
                "sip:" + realm);
        proxyAddr.setTransport(LinphoneAddress.TransportType.LinphoneTransportTcp);
        LinphoneProxyConfig proxyConfig = lc.createProxyConfig(
                "sip:" + username + "@" + realm, proxyAddr.asStringUriOnly(), null, true);
        proxyConfig.setExpires(300);  // TODO configure somewhere
        LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(
                username, password, realm, realm);
        lc.addProxyConfig(proxyConfig);
        lc.addAuthInfo(authInfo);
        lc.setDefaultProxyConfig(proxyConfig);
        lc.refreshRegisters();
    }

    @Nullable
    public AuthPair getLineConfig() throws LinphoneCoreException {
        LinphoneCore lc = getLinphoneCore();
        LinphoneAuthInfo[] authInfos = lc.getAuthInfosList();
        if (authInfos.length != 1) {
            return null;
        }
        String lineName = authInfos[0].getUsername();
        String lineHa1 = authInfos[0].getHa1();
        if (lineHa1 == null || lineName == null) {
            return null;
        }
        return new AuthPair(lineName, lineHa1);
    }

    public void clearLineConfig() throws LinphoneCoreException {
        LinphoneCore lc = getLinphoneCore();
        lc.clearAuthInfos();
        lc.clearProxyConfigs();
    }

    @NonNull
    public static LinphoneCore getLinphoneCore() throws LinphoneCoreException {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc == null) {
            throw new LinphoneCoreException("No LinphoneCore available");
        }
        return lc;
    }
}
