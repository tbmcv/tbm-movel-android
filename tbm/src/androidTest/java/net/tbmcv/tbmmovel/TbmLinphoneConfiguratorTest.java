package net.tbmcv.tbmmovel;

import android.test.AndroidTestCase;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TbmLinphoneConfiguratorTest extends AndroidTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!LinphoneManager.isInstanciated()) {
            LinphoneManager.createAndStart(getContext());
        }
        clearVoipLines();
    }

    protected void clearVoipLines() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.clearAuthInfos();
            lc.clearProxyConfigs();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        clearVoipLines();
        super.tearDown();
    }

    private <T> T getFromArrayOfOne(T... array) {
        assertEquals(1, array.length);
        return array[0];
    }

    private void assertEqualsOrNull(Object expected, Object actual) {
        if (actual != null) {
            assertEquals(expected, actual);
        }
    }

    void assertVoipLine(String realm, String lineName, String password) throws LinphoneCoreException {
        LinphoneCore lc = LinphoneManager.getLc();

        LinphoneAuthInfo auth = getFromArrayOfOne(lc.getAuthInfosList());
        assertEquals(lineName, auth.getUsername());
        assertEqualsOrNull(lineName, auth.getUserId());
        assertEquals(AcctDataService.createHa1(lineName, password, realm), auth.getHa1());
        assertEqualsOrNull(password, auth.getPassword());
        assertEquals(realm, auth.getDomain());
        assertEqualsOrNull(realm, auth.getRealm());

        LinphoneProxyConfig cfg = getFromArrayOfOne(lc.getProxyConfigList());
        assertEquals(realm, cfg.getDomain());
        LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(cfg.getProxy());
        assertEquals(realm, proxyAddr.getDomain());
        assertEquals(LinphoneAddress.TransportType.LinphoneTransportTcp, proxyAddr.getTransport());
        assertTrue(cfg.registerEnabled());
    }

    void addVoipLine(String lineName, String password, String domain) throws LinphoneCoreException {
        LinphoneCore lc = LinphoneManager.getLc();
        Set<LinphoneAuthInfo> oldAuthInfos = new HashSet<>();
        Collections.addAll(oldAuthInfos, lc.getAuthInfosList());
        new LinphonePreferences.AccountBuilder(lc)
                .setUsername(lineName)
                .setPassword(password)
                .setDomain(domain)
                .setRealm(domain)
                .setProxy(domain)
                .setEnabled(true)
                .saveNewAccount();
        Set<LinphoneAuthInfo> authInfos = new HashSet<>();
        Collections.addAll(authInfos, lc.getAuthInfosList());
        authInfos.removeAll(oldAuthInfos);
        assertEquals(1, authInfos.size());
        LinphoneAuthInfo authInfo = authInfos.iterator().next();
        authInfo.setPassword(null);
        authInfo.setHa1(AcctDataService.createHa1(lineName, password, domain));
    }

    public void testConfigureNewVoipLine() throws Exception {
        String realm = "sip.bandido.cv";
        String lineName = "tbm9999";
        String password = "pass";
        TbmLinphoneConfigurator.getInstance().configureLine(realm, lineName, password);
        assertVoipLine(realm, lineName, password);
    }

    public void testReconfigureVoipLine() throws Exception {
        String realm = "www.www.www";
        String lineName = "tbm5555";
        String password = "*****";
        addVoipLine("old", "values", "a.b.c");
        TbmLinphoneConfigurator.getInstance().configureLine(realm, lineName, password);
        assertVoipLine(realm, lineName, password);
    }
}
