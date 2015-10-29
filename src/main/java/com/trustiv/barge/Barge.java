package com.trustiv.barge;

import com.zaxxer.hikari.util.ConcurrentBag;
//import com.zaxxer.hikari.util.IBagStateListener;
//import com.zaxxer.hikari.util.IConcurrentBagEntry;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
//import com.zaxxer.hikari.util.Java8ConcurrentBag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Barge {
    public static final int ITERATIONS = 200000;
    public static final int WORKERS = 2; // Running on an 8 core machine, so shouldn't need to be pre-empted
    public static void main(String[] args) throws Exception {
        final Timestamper ts = new FairLockTimestamper();
        //final Timestamper ts = new AtomicLongTimestamper();
        //final Timestamper ts = new NanoTimeTimestamper();
        final ConcurrentBag<ManagableAsset> bag = new ConcurrentBag<>(new DummyListener());
        //final ConcurrentBag<ManagableAsset> bag = (ConcurrentBag) new Java8ConcurrentBag(new DummyListener()); // Dirty casting hackery to support 2.3.12
        //final BlockingQueue<ManagableAsset> queue = new ArrayBlockingQueue<>(1, true);
        //final BlockingQueue<ManagableAsset> queue = new ReallyUnfairQueue<>();

        List<Worker> workers = new ArrayList<>(WORKERS);
        for (int i = 0; i < WORKERS; i++) workers.add(new Worker(ts, bag));
        //for (int i = 0; i < WORKERS; i++) workers.add(new Worker(ts, queue));
        for (Worker worker: workers) worker.start();

        bag.add(new ManagableAsset()); // Single asset
        //queue.put(new ManagableAsset());

        for (Worker worker: workers) worker.join();

        Worker worker0 = workers.get(0);
        Worker worker1 = workers.get(1);
        double[] w0Data = worker0.startTimestamps;
        double[] w1Data = worker1.startTimestamps;

        // We count unfair runs - period when one worker kept on winning
        System.out.println("Longest unfair run was " + longestRun(w0Data, w1Data));

        // We KS the start times for the first two workers
        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        double result = ks.kolmogorovSmirnovTest(w0Data, w1Data);
        System.out.println("Kolmogorov-Smirnov test of startTimestamps has p-value " + result);

        Percentile w0Percentile = new Percentile();
        w0Percentile.setData(w0Data);
        Percentile w1Percentile = new Percentile();
        w1Percentile.setData(w1Data);

        System.out.println("Percentiles:");
        for (int i = 1; i <= 100; i++) {
            System.out.println(i + "," + w0Percentile.evaluate(i) + "," + w1Percentile.evaluate(i));
        }
    }

    private static int longestRun(double[] a, double[] b) {
        double startData = Math.min(a[0], b[0]);
        int currentRun = 0;
        int bestRun = 0;
        int aPointer = 0;
        int bPointer = 0;
        boolean isA = a[0] == startData;
        while (aPointer < a.length - 1 && bPointer < b.length - 1) {
            if (isA) {
                aPointer++;
                if (a[aPointer] < b[bPointer]) {
                    currentRun++;
                } else {
                    isA = false;
                    if (currentRun > bestRun) bestRun = currentRun;
                    currentRun = 0;
                }
            } else {
                bPointer++;
                if (a[aPointer] > b[bPointer]) {
                    currentRun++;
                } else {
                    isA = true;
                    if (currentRun > bestRun) bestRun = currentRun;
                    currentRun = 0;
                }
            }
        }
        if (currentRun > bestRun) bestRun = currentRun;
        return bestRun;
    }

    public static final class Worker extends Thread {
        public final double[] startTimestamps = new double[ITERATIONS];
        public final double[] endTimestamps = new double[ITERATIONS];
        private final Timestamper ts;
        //private final BlockingQueue<ManagableAsset> queue;
        private final ConcurrentBag<ManagableAsset> bag;
        private int wasteTimeSource = 0;

        private Worker(Timestamper ts, ConcurrentBag<ManagableAsset> bag) {
        //private Worker(Timestamper ts, BlockingQueue<ManagableAsset> queue) {
            this.ts = ts;
            this.bag = bag;
            //this.queue = queue;
        }

        @Override
        public void run() {
            for (int i = 0; i < ITERATIONS; i++) {
                startTimestamps[i] = ts.getTs();
                try {
                    ManagableAsset asset = bag.borrow(100, TimeUnit.MILLISECONDS);
                    //ManagableAsset asset = queue.poll(100, TimeUnit.MILLISECONDS);
                    while (asset == null) {
                        System.out.println(Thread.currentThread() + " starved for 100ms");
                        asset = bag.borrow(100, TimeUnit.MILLISECONDS);
                        //asset = queue.poll(100, TimeUnit.MILLISECONDS);
                    }
                    endTimestamps[i] = ts.getTs();
                    wasteTime(2000);
                    bag.requite(asset);
                    //queue.put(asset);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        private void wasteTime(int iterations) {
            for (int i = 0; i < iterations; i++)
                wasteTimeSource = wasteTimeSource * 768756959 + 21524963;
        }
    }

    public static final class ManagableAsset implements ConcurrentBag.IConcurrentBagEntry {
        private final AtomicInteger state = new AtomicInteger(0);

        public int getState() {
            return state.get();
        }

        public AtomicInteger state() {
            return state;
        }

        public boolean compareAndSetState(int expectedState, int newState) {
            return state.compareAndSet(expectedState, newState);
        }

        public boolean compareAndSet(int expectedState, int newState) {
            return state.compareAndSet(expectedState, newState);
        }
    }

    public static final class DummyListener implements ConcurrentBag.IBagStateListener {

        @Override
        public Future<Boolean> addBagItem() {
            return new Future<Boolean>() {

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isDone() {
                    return false;
                }

                @Override
                public Boolean get() throws InterruptedException, ExecutionException {
                    System.out.println("You're gonna regret getting this future");
                    for (;;) {Thread.sleep(1000L);}
                }

                @Override
                public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    Thread.sleep(unit.toMillis(timeout));
                    throw new TimeoutException();
                }
            };
        }
    }
}
