package com.trustiv.barge;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FairLockTimestamper implements Timestamper {
    private long ts = 0L;
    private Lock lock = new ReentrantLock(true);
    @Override
    public double getTs() {
        lock.lock();
        try {return ts++;}
        finally {lock.unlock();}
    }
}
