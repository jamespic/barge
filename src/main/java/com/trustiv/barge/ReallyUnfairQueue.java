package com.trustiv.barge;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Condition;

/**
 * A deliberately really unfair BlockingQueue, with capacity 1.
 * @param <E>
 */
public class ReallyUnfairQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {
    private volatile E entry;
    private static final AtomicReferenceFieldUpdater<ReallyUnfairQueue, Object> updater =
            AtomicReferenceFieldUpdater.newUpdater(ReallyUnfairQueue.class, Object.class, "entry");

    @Override
    public Iterator<E> iterator() {
        E e = entry;
        if (e == null) return Collections.emptyIterator();
        else return Collections.singleton(e).iterator();
    }

    @Override
    public E[] toArray(Object[] a) {
        E e = entry;
        if (e != null) a[0] = e;
        return (E[]) a;
    }

    @Override
    public int size() {
        return entry != null?1:0;
    }

    @Override
    public boolean add(E o) {
        if (!offer(o)) throw new IllegalStateException();
        return true;
    }

    @Override
    public synchronized boolean offer(E o) {
        boolean res = updater.compareAndSet(this, null, o);
        if (res) notifyAll();
        return res;
    }

    @Override
    public synchronized void put(E o) throws InterruptedException {
        while (!offer(o)) {
            wait();
        }
    }

    @Override
    public synchronized boolean offer(E o, long timeout, TimeUnit unit) throws InterruptedException {
        boolean res = false;
        long startTime = System.nanoTime();
        while (System.nanoTime() - startTime < unit.toNanos(timeout)) {
            if (offer(o)) return true;
            else wait(unit.toMillis(timeout));
        }
        return false;
    }

    @Override
    public E take() throws InterruptedException {
        E result = entry;
        if (result != null && updater.compareAndSet(this, result, null)) return result;
        synchronized(this) {
            while (true) {
                result = entry;
                if (result != null && updater.compareAndSet(this, result, null)) {
                    notifyAll();
                    return result;
                }
                else wait();
            }
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E result = entry;
        if (result != null && updater.compareAndSet(this, result, null)) return result;
        synchronized(this) {
            long startTime = System.nanoTime();
            while (System.nanoTime() - startTime < unit.toNanos(timeout)) {
                wait(unit.toMillis(timeout));
                result = entry;
                if (result != null && updater.compareAndSet(this, result, null)) {
                    notifyAll();
                    return result;
                }
            }
            return null;
        }
    }

    @Override
    public int remainingCapacity() {
        return 1 - size();
    }

    @Override
    public int drainTo(Collection c) {
        E e = poll();
        if (e != null) {
            c.add(e);
            return 1;
        } else return 0;

    }

    @Override
    public int drainTo(Collection c, int maxElements) {
        if (maxElements >= 1) return drainTo(c);
        else return 0;
    }

    @Override
    public E poll() {
        E result = entry;
        if (result != null && updater.compareAndSet(this, result, null)) return result;
        else return null;
    }

    @Override
    public E peek() {
        return entry;
    }
}
