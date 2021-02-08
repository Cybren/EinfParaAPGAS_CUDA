package group2;

import apgas.Configuration;
import apgas.Place;
import apgas.util.GlobalRef;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static apgas.Constructs.*;

public class Squares {

    //private static long[] primes;
    private static int maxPrimePos;
    private static ArrayList<Long> primes;
    private static GlobalRef<ArrayList<Long>> primeRef;
    private static GlobalRef<AtomicInteger> maxPrimePosRef;

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);

        int n = Integer.parseInt(args[0]);
        int m = Integer.parseInt(args[1]);
        long seedA = Integer.parseInt(args[2]);
        int d = Integer.parseInt(args[3]);
        int i = Integer.parseInt(args[4]);
        int verbose = Integer.parseInt(args[5]);

        int[][][] a = new int[n][n][n];
        long[][] zValue = new long[n][n];
        double[][] meanValue = new double[n][n];

        //primes = new long[m + 1];
        primes = new ArrayList<Long>();
        primeRef = new GlobalRef<>(primes);
        maxPrimePos = 0;
        maxPrimePosRef = new GlobalRef<>(new AtomicInteger(maxPrimePos));

        Configuration.APGAS_PLACES.setDefaultValue(4);
        Configuration.APGAS_THREADS.setDefaultValue(32);
        int t = Configuration.APGAS_THREADS.get();
        int p = Configuration.APGAS_PLACES.get();
        //final long nPerPlace = n / p;

        AtomicInteger min;
        AtomicInteger max;
        ArrayList<ArrayList<Integer>> minPos = new ArrayList<>();
        ArrayList<ArrayList<Integer>> maxPos = new ArrayList<>();
        //int[] maxPos = new int[3];

        Random random = new Random();

        long start = System.nanoTime();

        // Initialization
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    random.setSeed(seedA + Long.parseLong(x + "" + y + "" + z));
                    a[x][y][z] = random.nextInt(m) + 1;
                }
            }
        }

        // i iterations of computation
        for (int currentIteration = 0; currentIteration < i; currentIteration++) {

            long iterationStart = System.nanoTime();

            // compute zValue
            /*for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    //zValue[x][y] = 0;
                    for (int j = 0; j < n; j++) {
                        zValue[x][y] += findPrimeNumber(a[x][y][j]);
                    }
                }
            }*/

            final GlobalRef<long[][]> zVal = new GlobalRef<>(zValue);
            final int nPerPlace = n * n / p;
            final int nPerWorker = (int) (n*n)/(p*t)+1;

            finish(() -> {
                for (final Place place : places()) {
                    for (int worker = 0; worker < t; worker++) {
                        asyncAt(place, () -> {
                        for (int pos = place.id * nPerPlace; pos < (place.id + 1) * nPerPlace; pos++) {
                            long myZVal = 0;
                            int x = (int) (pos / n);
                            int y = pos % n;
                            for (int j = 0; j < n; j++) {
                                myZVal += findPrimeNumber(a[x][y][j]);
                            }
                            System.out.println("Hi from place " + place.id + ", thread " + getCurrentWorker().getId());
                            final long remoteResult = myZVal;
                            asyncAt(zVal.home(), () -> {
                                    zVal.get()[x][y] = zVal.get()[x][y] + remoteResult;
                        });
                        }
                        });
                    }
                }
            });

            // compute meanValue
            /*for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    int counter = 0;
                    meanValue[x][y] = 0;
                    for (int j = x - d; j <= x + d; j++) {
                        if (j >= 0 && j < n) {
                            for (int k = y - d; k <= y + d; k++) {
                                if (k >= 0 && k < n) {
                                    counter++;
                                    meanValue[x][y] += zValue[j][k];
                                }
                            }
                        }
                    }
                    meanValue[x][y] /= counter;
                }
            }*/

            final GlobalRef<double[][]> meanVal = new GlobalRef<>(meanValue);

            finish(() -> {
                for (final Place place : places()) {
                    asyncAt(place, () -> {
                        for (int pos = place.id * nPerPlace; pos < (place.id + 1) * nPerPlace; pos++) {
                            double myMeanVal = 0;
                            int counter = 0;
                            int y = pos % n;
                            int x = (int) (pos / n);
                            for (int j = x - d; j <= x + d; j++) {
                                if (j >= 0 && j < n) {
                                    for (int k = y - d; k <= y + d; k++) {
                                        if (k >= 0 && k < n) {
                                            counter++;
                                            myMeanVal += zValue[j][k];
                                        }
                                    }
                                }
                            }
                            myMeanVal /= counter;
                            final double remoteResult = myMeanVal;
                            //System.out.println(place.id + ": "+ pos + " -> [" + x + ", " + y + "] = " + remoteResult);
                            asyncAt((meanVal.home()), () -> {
                                meanVal.get()[x][y] = remoteResult;
                            });
                        }
                    });
                }
            });

            /*for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    meanValue[x][y] = meanVal.get()[x][y];
                }
            }*/


            // compute new a array
            /*for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    for (int z = 0; z < n; z++) {
                        random.setSeed((long) meanValue[x][y] + z);
                        // currentIteration is in range of 0..(i-1)
                        int bound = m + (int) (meanValue[x][y] / ((currentIteration + 1) * 50));
                        a[x][y][z] = random.nextInt(bound) + 1;
                    }
                }
            }*/

            final GlobalRef<int[][][]> ga = new GlobalRef<>(a);
            final int nnnPerPlace = n * n * n / p;

            final int iteration = currentIteration;

            finish(() -> {
                for (final Place place : places()) {
                    asyncAt(place, () -> {
                        for (int pos = place.id * nnnPerPlace; pos < (place.id + 1) * nnnPerPlace; pos++) {
                            //int z = pos % n;
                            //int y = ((pos-z) / n) % n;
                            //int x = (int) pos / (n*n);
                            for (int x = (int) (pos / (n * n)); x < n; x++) {
                                for (int y = (int) ((pos / n) % n); y < n; y++) {
                                    for (int z = pos % n; z < n; z++) {
                                        random.setSeed((long) meanValue[x][y] + z);
                                        // currentIteration is in range of 0..(i-1)
                                        int bound = m + (int) (meanValue[x][y] / ((iteration + 1) * 50));
                                        final int fx = x;
                                        final int fy = y;
                                        final int fz = z;
                                        final int result = random.nextInt(bound) + 1;
                                        asyncAt(ga.home(), () -> {
                                            ga.get()[fx][fy][fz] = result;
                                        });
                                    }
                                }
                            }
                        }
                    });
                }
            });

            /*for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    for (int z = 0; z < n; z++) {
                        a[x][y][z] = ga.get()[x][y][z];
                    }
                }
            }*/


            // resize primes-Array for next iteration
            /*int len = m + 1 + (int) ((n * primes[maxPrimePos]) / 50);
            long[] temp = new long[len];
            System.arraycopy(primes, 0, temp, 0, primes.length);
            primes = temp;*/

            // output of meanValue - matrix
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

        // To Do: compute min- and max-value and positions in a and output them

        //min = (int) primes[maxPrimePos] + 1;
        //min = (int) (primeRef.get().get(maxPrimePosRef.get()) + 1);
        //max = 0;

        final GlobalRef<AtomicInteger> minRef = new GlobalRef<>(new AtomicInteger((int) (primeRef.get().get(maxPrimePosRef.get().get()) + 1)));
        final GlobalRef<AtomicInteger> maxRef = new GlobalRef<>(new AtomicInteger(0));
        final int nnnPerPlace = n * n * n / p;

        finish(() -> {
            for (final Place place : places()) {
                asyncAt(place, () -> {
                    for (int pos = place.id * nnnPerPlace; pos < (place.id + 1) * nnnPerPlace; pos++) {
                        //int z = pos % n;
                        //int y = ((pos-z) / n) % n;
                        //int x = (int) pos / (n*n);
                        for (int x = (int) (pos / (n * n)); x < n; x++) {
                            for (int y = (int) ((pos / n) % n); y < n; y++) {
                                for (int z = pos % n; z < n; z++) {
                                    if (a[x][y][z] <= minRef.get().get()) {
                                        if (a[x][y][z] < minRef.get().get()) {
                                            minPos.clear();
                                        }
                                        minRef.get().set(a[x][y][z]);
                                        ArrayList<Integer> newMinPos = new ArrayList<>();
                                        newMinPos.add(x);
                                        newMinPos.add(y);
                                        newMinPos.add(z);
                                        minPos.add(newMinPos);
                                    }
                                    if (a[x][y][z] >= maxRef.get().get()) {
                                        if (a[x][y][z] > maxRef.get().get()) {
                                            maxPos.clear();
                                        }
                                        maxRef.get().set(a[x][y][z]);
                                        ArrayList<Integer> newMaxPos = new ArrayList<>();
                                        newMaxPos.add(x);
                                        newMaxPos.add(y);
                                        newMaxPos.add(z);
                                        maxPos.add(newMaxPos);
                                    }
                                }
                            }
                        }
                    }
                });
            }
        });

        /*for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    if (a[x][y][z] <= min) {
                        if (a[x][y][z] < min) {
                            minPos.clear();
                        }
                        min = a[x][y][z];
                        ArrayList<Integer> newMinPos = new ArrayList<>();
                        newMinPos.add(x);
                        newMinPos.add(y);
                        newMinPos.add(z);
                        minPos.add(newMinPos);
                    }
                    if (a[x][y][z] >= max) {
                        if (a[x][y][z] > max) {
                            maxPos.clear();
                        }
                        max = a[x][y][z];
                        ArrayList<Integer> newMaxPos = new ArrayList<>();
                        newMaxPos.add(x);
                        newMaxPos.add(y);
                        newMaxPos.add(z);
                        maxPos.add(newMaxPos);
                    }
                }
            }
        }*/

        System.out.println("Min=" + minRef.get().get() + " " + minPos);
        System.out.println("Max=" + maxRef.get().get() + " " + maxPos);

        /*System.out.println(String.format("Min=%d (%d,%d,%d)", min, minPos[0], minPos[1], minPos[2]));
        System.out.println(String.format("Max=%d (%d,%d,%d)", max, maxPos[0], maxPos[1], maxPos[2]));*/


        long end = System.nanoTime();

        System.out.println("Process time=" + ((end - start) / 1E9D) + " sec");
    }

    // with saving prime numbers
    /*public static long findPrimeNumber(int x) {
        if (x <= maxPrimePos) {
            return primes[x];
        }
        int count = maxPrimePos;
        long a;
        if (maxPrimePos == 0) {
            a = 2;
        } else {
            a = primes[maxPrimePos] + 1;
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
            if (prime > 0) {
                count++;
                primes[count] = a;
                maxPrimePos++;
            }
            a++;
        }
        return (--a);
    }*/

    // with ArrayList
    /*public static long findPrimeNumber(int x) {
        if (x <= maxPrimePos) {
            return primes.get(x);
        }
        int count = maxPrimePos;
        long a;
        if (maxPrimePos == 0) {
            a = 2;
            primes.add(0, 0l);
        } else {
            a = primes.get(maxPrimePos) + 1;
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
            if (prime > 0) {
                count++;
                primes.add(count, a);
                maxPrimePos++;
            }
            a++;
        }
        return (--a);
    }*/

    // with ArrayList and GlobalRef
    public static long findPrimeNumber(int x) {
        if (x <= maxPrimePosRef.get().get()) {
            return primeRef.get().get(x);
        }
        int count = maxPrimePosRef.get().get();
        long a;
        if (maxPrimePosRef.get().get() == 0) {
            a = 2;
            primeRef.get().add(0, 0l);
        } else {
            a = primeRef.get().get(maxPrimePosRef.get().get()) + 1;
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
            if (prime > 0) {
                count++;
                primeRef.get().add(count, a);
                maxPrimePosRef.get().addAndGet(1);//set(maxPrimePosRef.get().get() + 1);
            }
            a++;
        }
        return (--a);
    }

    // without saving prime numbers
    /*public static long findPrimeNumber(int x) {
        int count = 0;
        long a = 2;
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
            if (prime > 0) {
                count++;
            }
            a++;
        }
        return (--a);
    }*/


}
