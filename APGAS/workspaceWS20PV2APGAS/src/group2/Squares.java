package group2;

import apgas.Configuration;
import apgas.util.GlobalRef;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static apgas.Constructs.*;

public class Squares {

    static List<Long> primes = new ArrayList<Long>();
    final static GlobalRef<List<Long>> primeRef = new GlobalRef<>(primes);

    static final Object LOCK_PRIMES = new Object();

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);

        int n = Integer.parseInt(args[0]);
        int m = Integer.parseInt(args[1]);
        long seedA = Integer.parseInt(args[2]);
        int d = Integer.parseInt(args[3]);
        int i = Integer.parseInt(args[4]);
        int verbose = Integer.parseInt(args[5]);

        System.out.println("java group2.Squares " + n + " " + m + " " + seedA + " " + d + " " + i + " " + verbose + "\n");

        int[][][] a = new int[n][n][n];
        long[][] zValue = new long[n][n];
        double[][] meanValue = new double[n][n];

        // global references
        final GlobalRef<long[][]> zValRef = new GlobalRef<>(zValue);
        final GlobalRef<double[][]> meanValRef = new GlobalRef<>(meanValue);
        final GlobalRef<int[][][]> aRef = new GlobalRef<>(a);

        int t = Configuration.APGAS_THREADS.get();
        int p = Configuration.APGAS_PLACES.get();

        long start = System.nanoTime();

        // Initialization
        Random random = new Random();
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    random.setSeed(seedA + Long.parseLong(x + "" + y + "" + z));
                    a[x][y][z] = random.nextInt(m) + 1;
                }
            }
        }

        // start computing prime numbers
        if (t > 1) {
            async(() -> {
                findPrimeNumber(m * i);
            });
        }

        // i iterations of computation
        for (int currentIteration = 0; currentIteration < i; currentIteration++) {

            long iterationStart = System.nanoTime();

            // compute zValue
            finish(() -> {
                int startVal = 0;
                int stopVal = n * n;
                int placeNum = 0;

                while (startVal < stopVal) {

                    // compute the blocksize at the places
                    int blockSize;
                    if (t == 1) {
                        blockSize = (n * n) / (p * t) + 1;
                    } else {
                        blockSize = (n * n) / ((p - 1) * t + (t/2)) + 1;
                    }
                    int placeBlockSize;
                    if (placeNum > 0 || t == 1) {
                        placeBlockSize = t * blockSize;
                    } else {
                        placeBlockSize = (t/2) * blockSize;
                    }

                    // split the blocks to the places
                    final int placeStartVal = startVal;
                    final int placeStopVal = Math.min(startVal + placeBlockSize, stopVal);
                    asyncAt(place(placeNum), () -> {

                        // split blocks to the threads of a place
                        int threadStartVal = placeStartVal;
                        int threadBlockSize = Math.min(placeBlockSize / t + 1, placeStopVal);
                        int threadStopVal;
                        while (threadStartVal < placeStopVal) {

                            long[] localZVal = new long[threadBlockSize];
                            threadStopVal = Math.min(threadStartVal + threadBlockSize, placeStopVal);
                            final int fstart = threadStartVal;
                            final int fstop = threadStopVal;
                            async(() -> {
                                // compute zValues
                                int x, y;
                                for (int pos = fstart; pos < fstop; pos++) {
                                    x = (int) (pos / n);
                                    y = pos % n;
                                    for (int j = 0; j < n; j++) {
                                        localZVal[pos - fstart] += findPrimeNumber(a[x][y][j]);
                                    }
                                }

                                // save zValues
                                final long[] remoteZVal = localZVal;
                                asyncAt(zValRef.home(), () -> {
                                    for (int pos = fstart; pos < fstop; pos++) {
                                        zValRef.get()[(int) (pos / n)][pos % n] = zValRef.get()[(int) (pos / n)][pos % n] + remoteZVal[pos - fstart];
                                    }
                                });
                            });
                            threadStartVal += threadBlockSize;
                        }
                    });
                    placeNum = (placeNum + 1) % p;
                    startVal += placeBlockSize;
                }
            });


            // compute meanValue
            finish(() -> {
                int startVal = 0;
                int stopVal = n * n;
                int placeNum = 0;

                while (startVal < stopVal) {

                    // compute blocksize at every place
                    int blockSize;
                    if (t == 1) {
                        blockSize = (n * n) / (p * t) + 1;
                    } else {
                        blockSize = (n * n) / ((p - 1) * t + (int) (0.75*t)) + 1;
                    }
                    int placeBlockSize;
                    if (placeNum > 0 || t == 1) {
                        placeBlockSize = t * blockSize;
                    } else {
                        placeBlockSize = (int) (0.75*t) * blockSize;
                    }

                    final int placeStartVal = startVal;
                    final int placeStopVal = Math.min(startVal + placeBlockSize, stopVal);

                    asyncAt(place(placeNum), () -> {

                        // compute blocksize for the threads
                        int threadStartVal = placeStartVal;
                        int threadBlockSize = Math.min(placeBlockSize / t + 1, placeStopVal);
                        int threadStopVal;
                        while (threadStartVal < placeStopVal) {

                            double[] localMeanVal = new double[threadBlockSize];
                            threadStopVal = Math.min(threadStartVal + threadBlockSize, placeStopVal);
                            final int fstart = threadStartVal;
                            final int fstop = threadStopVal;
                            async(() -> {
                                // compute meanValues
                                int x, y;
                                for (int pos = fstart; pos < fstop; pos++) {
                                    x = (int) (pos / n);
                                    y = pos % n;
                                    int counter = 0;
                                    for (int j = x - d; j <= x + d; j++) {
                                        if (j >= 0 && j < n) {
                                            for (int k = y - d; k <= y + d; k++) {
                                                if (k >= 0 && k < n) {
                                                    counter++;
                                                    localMeanVal[pos - fstart] += zValue[j][k];
                                                }
                                            }
                                        }
                                    }
                                    localMeanVal[pos - fstart] /= counter;
                                }

                                // save meanValues
                                final double[] remoteMeanVal = localMeanVal;
                                asyncAt(meanValRef.home(), () -> {
                                    for (int pos = fstart; pos < fstop; pos++) {
                                        meanValRef.get()[(int) (pos / n)][pos % n] = remoteMeanVal[pos - fstart];
                                    }
                                });
                            });
                            threadStartVal += threadBlockSize;
                        }
                    });
                    startVal += placeBlockSize;
                    placeNum = (placeNum + 1) % p;
                }
            });


            // compute new a array
            final int iteration = currentIteration;
            finish(() -> {
                int startVal = 0;
                int stopVal = n * n * n;
                int placeNum = 0;

                while (startVal < stopVal) {

                    // compute blocksize of every place
                    int blockSize;
                    int placeBlockSize;
                    if (t == 1) {
                        blockSize = (n * n * n) / (p * t) + 1;
                    } else {
                        blockSize = (n * n * n) / ((p - 1) * t + (int) (t * 0.75)) + 1;
                    }
                    if (placeNum > 0 || t == 1) {
                        placeBlockSize = t * blockSize;
                    } else {
                        placeBlockSize = (int) (t * 0.75) * blockSize;
                    }

                    final int placeStartVal = startVal;
                    final int placeStopVal = Math.min(startVal + placeBlockSize, stopVal);

                    asyncAt(place(placeNum), () -> {

                        // compute blocksize of every thread
                        int threadStartVal = placeStartVal;
                        int threadBlockSize = Math.min(placeBlockSize / t + 1, placeStopVal);
                        int threadStopVal;
                        while (threadStartVal < placeStopVal) {

                            int[] localA = new int[threadBlockSize];
                            threadStopVal = Math.min(threadStartVal + threadBlockSize, placeStopVal);
                            final int fstart = threadStartVal;
                            final int fstop = threadStopVal;
                            async(() -> {

                                // compute new a values
                                int x, y, z;
                                for (int pos = fstart; pos < fstop; pos++) {
                                    x = (int) (pos / (n * n));
                                    y = (int) ((pos / n) % n);
                                    z = pos % n;

                                    Random localRandom = new Random();
                                    localRandom.setSeed((long) meanValue[x][y] + z);
                                    // currentIteration is in range of 0..(i-1)
                                    int bound = m + (int) (meanValue[x][y] / ((iteration + 1) * 50));
                                    localA[pos - fstart] = localRandom.nextInt(bound) + 1;
                                }

                                // save values of a
                                final int[] remoteA = localA;
                                asyncAt(aRef.home(), () -> {
                                    for (int pos = fstart; pos < fstop; pos++) {
                                        int fx = (int) (pos / (n * n));
                                        int fy = (int) ((pos / n) % n);
                                        int fz = pos % n;
                                        aRef.get()[fx][fy][fz] = remoteA[pos - fstart];
                                    }
                                });
                            });
                            threadStartVal += threadBlockSize;
                        }
                    });
                    placeNum = (placeNum + 1) % p;
                    startVal += placeBlockSize;
                }
            });


            // output of meanValue
            if (verbose == 1) {
                for (int x = 0; x < n; x++) {
                    for (int y = 0; y < n; y++) {
                        System.out.printf("%.2f%c ", meanValue[x][y], (y < n - 1 ? ',' : ' '));
                    }
                    System.out.println();
                }
                long iterationEnd = System.nanoTime();
                System.out.println("Iteration " + currentIteration + " time=" + ((iterationEnd - iterationStart) / 1E9D) + " sec");
                System.out.println();
            }

        }

        // compute min- and max-value and positions in a and output them

        AtomicInteger minimum = new AtomicInteger((int) (primes.size() + 1));
        AtomicInteger maximum = new AtomicInteger(0);
        ConcurrentHashMap<Integer, ArrayList<Position>> minPos = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, ArrayList<Position>> maxPos = new ConcurrentHashMap<>();

        GlobalRef<AtomicInteger> minRef = new GlobalRef<>(minimum);
        GlobalRef<AtomicInteger> maxRef = new GlobalRef<>(maximum);
        GlobalRef<ConcurrentHashMap<Integer, ArrayList<Position>>> minPosRef = new GlobalRef<>(minPos);
        GlobalRef<ConcurrentHashMap<Integer, ArrayList<Position>>> maxPosRef = new GlobalRef<>(maxPos);

        finish(() -> {
            int startVal = 0;
            int stopVal = n * n * n;
            int placeNum = 0;

            // blocksize at places
            int blockSize = n * n * n / p + 1;
            while (startVal < stopVal) {

                // blocksize at threads
                int workerBlockSize = (int) (blockSize / t) + 1;
                for (int worker = startVal; (worker < startVal + blockSize) && (worker < stopVal); worker += workerBlockSize) {
                    final int thisStartVal = worker;
                    final int thisBlockSize = workerBlockSize;
                    final int thisStopVal = Math.min(startVal + blockSize, stopVal);
                    asyncAt(place(placeNum), () -> {
                        int x, y, z;

                        // get global min- and max-values
                        int myMinVal = at(minRef.home(), () -> minRef.get().get());
                        int myMaxVal = at(maxRef.home(), () -> maxRef.get().get());
                        ArrayList<Position> myMinPos = new ArrayList<>();
                        ArrayList<Position> myMaxPos = new ArrayList<>();
                        boolean newMin = false;
                        boolean newMax = false;
                        for (int pos = thisStartVal; pos < (thisStartVal + thisBlockSize) && pos < thisStopVal; pos++) {
                            x = (int) (pos / (n * n));
                            y = (int) ((pos / n) % n);
                            z = pos % n;

                            // search for values <= myMinValue
                            if (a[x][y][z] <= myMinVal) {
                                if (a[x][y][z] < myMinVal) {
                                    myMinVal = a[x][y][z];
                                    myMinPos.clear();
                                }
                                Position newPos = new Position(x, y, z);
                                myMinPos.add(newPos);
                                newMin = true;
                            }

                            // search for values >= myMaxValue
                            if (a[x][y][z] >= myMaxVal) {
                                if (a[x][y][z] > myMaxVal) {
                                    myMaxVal = a[x][y][z];
                                    myMaxPos.clear();
                                }
                                Position newPos = new Position(x, y, z);
                                myMaxPos.add(newPos);
                                newMax = true;
                            }
                        }
                        if (newMin) {
                            final int fmin = myMinVal;
                            final ArrayList<Position> fMinPos = myMinPos;

                            // update global minimum
                            final int globalMinVal = at(minRef.home(), () -> {
                                if (fmin < minRef.get().get()) {
                                    minRef.get().getAndSet(fmin);
                                }
                                return minRef.get().get();
                            });

                            // update positions of global minimum
                            asyncAt(minPosRef.home(), () -> {
                                if (fmin <= globalMinVal) {
                                    minPosRef.get().putIfAbsent(fmin, new ArrayList<Position>());
                                    for (Position pos : fMinPos) {
                                        minPosRef.get().get(fmin).add(pos);
                                    }
                                }
                            });
                        }
                        if (newMax) {
                            final int fmax = myMaxVal;
                            final ArrayList<Position> fMaxPos = myMaxPos;

                            // update global maximum
                            final int globalMaxVal = at(maxRef.home(), () -> {
                                if (fmax > maxRef.get().get()) {
                                    maxRef.get().getAndSet(fmax);
                                }
                                return maxRef.get().get();
                            });

                            // update positions of global maximum
                            asyncAt(maxPosRef.home(), () -> {
                                if (fmax >= globalMaxVal) {
                                    maxPosRef.get().putIfAbsent(fmax, new ArrayList<Position>());
                                    for (Position pos : fMaxPos) {
                                        maxPosRef.get().get(fmax).add(pos);
                                    }
                                }
                            });
                        }
                    });
                }
                startVal += blockSize;
                placeNum = (placeNum + 1) % p;
            }
        });

        ArrayList<Position> minimumPos = minPos.get(minimum.get());
        ArrayList<Position> maximumPos = maxPos.get(maximum.get());


        // sort and output positions of minimum
        Collections.sort(minimumPos);
        System.out.print("Min=" + minimum + " ");
        for (Position pos : minimumPos) {
            System.out.print(pos);
        }
        System.out.println();

        // sort and output positions of maximum
        Collections.sort(maximumPos);
        System.out.print("Max=" + maximum + " ");
        for (Position pos : maximumPos) {
            System.out.print(pos);
        }
        System.out.println();

        long end = System.nanoTime();
        System.out.println("Process time=" + ((end - start) / 1E9D) + " sec");
    }

    // with ArrayList and GlobalRef
    public static long findPrimeNumber(int x) {

        // get list of primes
        List<Long> myPrimes = primeRef.get();
        int maxPrimePos = Math.max(myPrimes.size() - 1, 0);

        // if prime(x) already computed: return
        if (x <= maxPrimePos) {
            return myPrimes.get(x);
        }

        int count = maxPrimePos;
        long a;
        // set a
        if (maxPrimePos <= 0) {
            a = 2;
            at(primeRef.home(), () -> {
                synchronized (LOCK_PRIMES) {
                    if (primeRef.get().size() <= 0) {
                        primeRef.get().add(0, 0l);
                    }
                }
            });
        } else {
            a = myPrimes.get(maxPrimePos) + 1;
        }

        while (count < x) {
            long b = 2;
            int prime = 1;  // to check if found a prime
            while (b * b <= a) {
                if (a % b == 0) {
                    prime = 0;
                    break;
                }
                b++;
            }

            // next prime found
            if (prime > 0) {
                count++;
                final long fPrime = a;
                final int c = count;
                at(primeRef.home(), () -> {
                    // add found prime, if not already added by another thread
                    synchronized (LOCK_PRIMES) {
                        if (primeRef.get().size() <= c) {
                            primeRef.get().add(c, fPrime);
                        }
                    }
                });
                // get actualized primes-array
                myPrimes = primeRef.get();
                maxPrimePos = myPrimes.size() - 1;
                if (x <= maxPrimePos) {
                    return myPrimes.get(x);
                } else {
                    count = maxPrimePos;
                    a = myPrimes.get(maxPrimePos);
                }
            }
            a++;
        }
        return (--a);
    }

}

class Position implements Comparable<Position>, Serializable {

    private static final long serialVersionUID = 12345L;

    int x, y, z;

    public Position(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int compareTo(Position o) {
        if (this.x != o.x) {
            return this.x - o.x;
        }
        if (this.y != o.y) {
            return this.y - o.y;
        }
        if (this.z != o.z) {
            return this.z - o.z;
        }
        return 0;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ") ";
    }
}
