package com.trustiv.barge;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicLongTimestamper implements Timestamper {
    private final AtomicLong l = new AtomicLong(0L);
    @Override
    public double getTs() {
        while (true) {
            long prev = l.get();
            if (l.weakCompareAndSet(prev, prev + 1)) { // Weak CAS, to prevent artificial write barrier
                return prev;
            }
        }
    }
}
