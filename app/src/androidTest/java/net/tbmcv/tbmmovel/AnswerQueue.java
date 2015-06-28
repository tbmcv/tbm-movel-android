package net.tbmcv.tbmmovel;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;

public class AnswerQueue<T> implements Answer<T> {
    private static final Map<AnswerQueue<?>, ?> allAnswerQueues = new WeakHashMap<>();

    private final Queue<AnswerPair<T>> queue = new LinkedList<>();
    private boolean unblocked = false;
    private T emptyResult;

    public AnswerQueue(T emptyResult) {
        this.emptyResult = emptyResult;
        synchronized (allAnswerQueues) {
            allAnswerQueues.put(this, null);
        }
    }

    public AnswerQueue() {
        this(null);
    }

    @Override
    public synchronized T answer(InvocationOnMock invocation) throws Throwable {
        while (queue.isEmpty()) {
            if (isUnblocked()) {
                return emptyResult;
            }
            wait();
        }
        AnswerPair<T> item = queue.remove();
        item.latch.countDown();
        return item.value;
    }

    public synchronized CountDownLatch add(T result) {
        AnswerPair<T> item = new AnswerPair<>(result);
        queue.add(item);
        notifyAll();
        return item.latch;
    }

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized void setEmptyResult(T emptyResult) {
        this.emptyResult = emptyResult;
    }

    public synchronized boolean isUnblocked() {
        return unblocked;
    }

    public synchronized void setUnblocked(boolean unblocked) {
        this.unblocked = unblocked;
        if (unblocked) {
            notifyAll();
        }
    }

    private static class AnswerPair<T> {
        final T value;
        final CountDownLatch latch = new CountDownLatch(1);

        AnswerPair(T value) {
            this.value = value;
        }
    }

    public static void unblockAll() {
        synchronized (allAnswerQueues) {
            for (AnswerQueue<?> queue : allAnswerQueues.keySet()) {
                queue.setUnblocked(true);
                queue.clear();
            }
        }
    }
}
