package net.tbmcv.tbmmovel;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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

    public AnswerQueue(@Nullable T emptyResult) {
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
        if (item.error == null) {
            return item.value;
        } else {
            throw item.error;
        }
    }

    public synchronized CountDownLatch addResult(@Nullable T result) {
        AnswerPair<T> item = new AnswerPair<>(result, null);
        queue.add(item);
        notifyAll();
        return item.latch;
    }

    public synchronized CountDownLatch addError(@NonNull Throwable error) {
        AnswerPair<T> item = new AnswerPair<>(null, error);
        queue.add(item);
        notifyAll();
        return item.latch;
    }

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized void setEmptyResult(@Nullable T emptyResult) {
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
        final CountDownLatch latch = new CountDownLatch(1);
        final T value;
        final Throwable error;

        AnswerPair(@Nullable T value, @Nullable Throwable error) {
            this.value = value;
            this.error = error;
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
