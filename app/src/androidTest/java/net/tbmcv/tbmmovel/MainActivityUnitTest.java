package net.tbmcv.tbmmovel;

import android.content.Context;
import android.content.SharedPreferences;

public class MainActivityUnitTest extends BaseActivityUnitTest<MainActivity> {
    public MainActivityUnitTest() {
        super(MainActivity.class);
    }

    public void testActivitySwitchesIfUnconfigured() {
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.settings_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launch();
        getInstrumentation().callActivityOnResume(getActivity());
        getInstrumentation().waitForIdleSync();
        assertLaunched(InitConfigActivity.class);
        assertTrue("Finish not called", isFinishCalled());
    }

    public void testNoActivitySwitchIfConfigured() {
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.settings_key), Context.MODE_PRIVATE);
        prefs.edit()
                .clear()
                .putString(context.getString(R.string.setting_acctname), "c/9345678")
                .putString(context.getString(R.string.setting_password), "blah")
                .commit();
        launch();
        getInstrumentation().callActivityOnResume(getActivity());
        getInstrumentation().waitForIdleSync();
        assertNull("Another Activity started", getStartedActivityIntent());
        assertFalse("Finish called", isFinishCalled());
    }
}
