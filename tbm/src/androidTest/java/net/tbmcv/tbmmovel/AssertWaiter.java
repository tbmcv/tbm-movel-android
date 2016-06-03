package net.tbmcv.tbmmovel;

import java.util.concurrent.TimeUnit;

public abstract class AssertWaiter {
    protected abstract void test() throws Exception;

    public void await(long timeout, TimeUnit unit) throws Exception {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        AssertionError assertionError;
        do {
            try {
                test();
                return;
            } catch (AssertionError e) {
                assertionError = e;
                Thread.sleep(1);
            }
        } while (System.currentTimeMillis() < endTime);
        throw assertionError;
    }

    public void await() throws Exception {
        await(2, TimeUnit.SECONDS);
    }
}
