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
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListener;
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
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        lc.enableAdaptiveRateControl(false);
        lc.enableKeepAlive(true);
        for (PayloadType codec : lc.getAudioCodecs()) {
            lc.enablePayloadType(codec, codecDefaultEnabled(codec));
        }
    }

    public void setDefaultNetworkSettings() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        lc.enableKeepAlive(true);
        lc.enableDnsSrv(false);
        lc.setStunServer(null);
    }

    public void startEchoCalibration(LinphoneCoreListener listener) throws LinphoneCoreException {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null && lc.needsEchoCalibration()) {
            lc.startEchoCalibration(listener);
        }
    }

    public boolean codecDefaultEnabled(PayloadType codec) {
        return codec.getRate() == 8000 && "GSM".equals(codec.getMime());
    }
}
