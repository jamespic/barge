package com.trustiv.barge;

public class NanoTimeTimestamper implements Timestamper {
    @Override
    public double getTs() {
        return System.nanoTime();
    }
}
