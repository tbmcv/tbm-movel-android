package net.tbmcv.tbmmovel;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;

public abstract class BaseFragmentUnitTest<F extends Fragment>
        extends BaseActivityUnitTest<BaseFragmentUnitTest.TestActivity> {
    static final String FRAGMENT_TAG = "FragmentTag";

    public static final class TestActivity extends FragmentActivity {
        @Override
        public void onPostResume() {
            super.onPostResume();
        }
    }

    protected abstract F createFragment();

    public BaseFragmentUnitTest() {
        super(TestActivity.class);
    }

    protected void startAndResumeAll() {
        launch();
        TestActivity activity = getActivity();
        getInstrumentation().callActivityOnStart(activity);
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, createFragment(), FRAGMENT_TAG)
                .commit();
        getInstrumentation().callActivityOnResume(activity);
        activity.onPostResume();
        getInstrumentation().waitForIdleSync();
    }
}
