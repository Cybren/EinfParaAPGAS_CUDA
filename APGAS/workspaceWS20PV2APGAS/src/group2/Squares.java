package group2;

import apgas.Configuration;
import apgas.Place;
import apgas.util.GlobalRef;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static apgas.Constructs.*;

public class Squares {

    static AtomicInteger maxPrimePos = new AtomicInteger(0);
    static List<Long> primes = Collections.synchronizedList(new ArrayList<Long>());
    final static GlobalRef<List<Long>> primeRef = new GlobalRef<>(primes);
    final static GlobalRef<AtomicInteger> maxPrimePosRef = new GlobalRef<>(maxPrimePos);

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

        Configuration.APGAS_PLACES.setDefaultValue(4);
        Configuration.APGAS_THREADS.setDefaultValue(32);
        int t = Configuration.APGAS_THREADS.get();
        int p = Configuration.APGAS_PLACES.get();

        // global references
        final GlobalRef<long[][]> zValRef = new GlobalRef<>(zValue);
        final GlobalRef<double[][]> meanValRef = new GlobalRef<>(meanValue);
        final GlobalRef<int[][][]> aRef = new GlobalRef<>(a);

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

            final int nPerPlace = n * n / p;
            final int nPerWorker = (int) (n * n) / (p * t) + 1;

            /*finish(() -> {
                for (final Place place : places()) {
                    //for (int worker = 0; worker < t; worker++) {
                    asyncAt(place, () -> {
                        for (int pos = place.id * nPerPlace; pos < (place.id + 1) * nPerPlace; pos++) {
                            long myZVal = 0;
                            int x = (int) (pos / n);
                            int y = pos % n;
                            for (int j = 0; j < n; j++) {
                                myZVal += findPrimeNumber(a[x][y][j]);
                            }
                            final long remoteResult = myZVal;
                            asyncAt(zVal.home(), () -> {
                                zVal.get()[x][y] = zVal.get()[x][y] + remoteResult;
                            });
                        }
                    });
                }
            });*/

            // compute zValue
            long zValStart = System.nanoTime();

            finish(() -> {
                int startVal = 0;
                int blockSize = n * n / p;  // anpassen
                int placeNum = (places().size() > 1 ? 1 : 0);
                while (startVal < n * n) {
                    final int thisStartVal = startVal;
                    final int thisBlockSize = blockSize;
                    asyncAt(place(placeNum), () -> {
                        int x, y;
                        long[] myZVal = new long[thisBlockSize];
                        for (int pos = thisStartVal; pos < (thisStartVal + thisBlockSize) && pos < n * n; pos++) {
                            x = (int) (pos / n);
                            y = pos % n;
                            for (int j = 0; j < n; j++) {
                                myZVal[pos - thisStartVal] += findPrimeNumber(a[x][y][j]);
                            }
                        }
                        final long[] remoteZVal = myZVal;
                        asyncAt(zValRef.home(), () -> {
                            for (int pos = thisStartVal; pos < (thisStartVal + thisBlockSize) && pos < n * n; pos++) {
                                zValRef.get()[(int) (pos / n)][pos % n] = zValRef.get()[(int) (pos / n)][pos % n] + remoteZVal[pos - thisStartVal];
                            }
                        });
                    });
                    startVal += blockSize;
                    placeNum = (placeNum + 1) % p;
                    if (placeNum == 0) {
                        //placeNum += 1;
                        blockSize *= 1; // anpassen
                    }
                }
            });

            long zValEnd = System.nanoTime();
            System.out.println("zValue : time=" + ((zValEnd - zValStart) / 1E9D) + " sec");


            // compute meanValue
            long meanValStart = System.nanoTime();

            /*finish(() -> {
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
            });*/

            finish(() -> {
                int startVal = 0;
                int blockSize = n * (n / 2) / p; // anpassen
                int placeNum = (places().size() > 1 ? 1 : 0);
                while (startVal < n * n) {
                    final int thisStartVal = startVal;
                    final int thisBlockSize = blockSize;
                    asyncAt(place(placeNum), () -> {
                        int x, y;
                        double[] myMeanVal = new double[thisBlockSize];
                        for (int pos = thisStartVal; pos < (thisStartVal + thisBlockSize) && pos < n * n; pos++) {
                            x = (int) (pos / n);
                            y = pos % n;
                            int counter = 0;
                            for (int j = x - d; j <= x + d; j++) {
                                if (j >= 0 && j < n) {
                                    for (int k = y - d; k <= y + d; k++) {
                                        if (k >= 0 && k < n) {
                                            counter++;
                                            myMeanVal[pos - thisStartVal] += zValue[j][k];
                                        }
                                    }
                                }
                            }
                            myMeanVal[pos - thisStartVal] /= counter;
                            //System.out.println(here() + ": " + myMeanVal[pos - thisStartVal]);
                        }
                        final double[] remoteMeanVal = myMeanVal;
                        asyncAt(meanValRef.home(), () -> {
                            for (int pos = thisStartVal; pos < (thisStartVal + thisBlockSize) && pos < n * n; pos++) {
                                meanValRef.get()[(int) (pos / n)][pos % n] = remoteMeanVal[pos - thisStartVal];
                            }
                        });
                    });
                    startVal += blockSize;
                    placeNum = (placeNum + 1) % p;
                    if (placeNum == 0) {
                        blockSize *= 1; // anpassen
                    }
                }
            });

            long meanValEnd = System.nanoTime();
            System.out.println("meanValue : time=" + ((meanValEnd - meanValStart) / 1E9D) + " sec");


            // compute new a array
            final int iteration = currentIteration;

            long aStart = System.nanoTime();

            /*finish(() -> {
                for (final Place place : places()) {
                    asyncAt(place, () -> {
                        int[][][] myA = new int[n / p][n][n];
                        final int xStart = (int) ((place.id * nnnPerPlace) / (n * n));
                        for (int pos = place.id * nnnPerPlace; pos < (place.id + 1) * nnnPerPlace; pos++) {
                            int x = (int) (pos / (n * n));
                            int y = (int) ((pos / n) % n);
                            int z = pos % n;

                            random.setSeed((long) meanValue[x][y] + z);
                            // currentIteration is in range of 0..(i-1)
                            int bound = m + (int) (meanValue[x][y] / ((iteration + 1) * 50));
                            myA[x - xStart][y][z] = random.nextInt(bound) + 1;
                        }
                        asyncAt(ga.home(), () -> {
                            for (int x = 0; x < (int) (n / p); x++) {
                                ga.get()[x + xStart] = myA[x];
                            }
                        });
                    });
                }
            });*/

            finish(() -> {
                int startVal = 0;
                int blockSize = n * n * n / p;  // anpassen
                int placeNum = (places().size() > 1 ? 1 : 0);
                while (startVal < n * n * n) {
                    final int thisStartVal = startVal;
                    final int thisBlockSize = blockSize;
                    asyncAt(place(placeNum), () -> {
                        int[] myA = new int[thisBlockSize];
                        for (int pos = thisStartVal; pos < (thisStartVal + thisBlockSize) && pos < n * n * n; pos++) {
                            int x = (int) (pos / (n * n));
                            int y = (int) ((pos / n) % n);
                            int z = pos % n;

                            random.setSeed((long) meanValue[x][y] + z);
                            // currentIteration is in range of 0..(i-1)
                            int bound = m + (int) (meanValue[x][y] / ((iteration + 1) * 50));
                            myA[pos - thisStartVal] = random.nextInt(bound) + 1;
                        }
                        final int[] remoteA = myA;
                        asyncAt(aRef.home(), () -> {
                            for (int pos = thisStartVal; pos < (thisStartVal + thisBlockSize) && pos < n * n * n; pos++) {
                                int x = (int) (pos / (n * n));
                                int y = (int) ((pos / n) % n);
                                int z = pos % n;
                                aRef.get()[x][y][z] = remoteA[pos - thisStartVal];
                            }
                        });
                    });
                    startVal += blockSize;
                    placeNum = (placeNum + 1) % p;
                    if (placeNum == 0) {
                        blockSize *= 1; // anpassen
                    }
                }
            });

            long aEnd = System.nanoTime();
            System.out.println("new a : time=" + ((aEnd - aStart) / 1E9D) + " sec");


            // output of meanValue - matrix
            if (verbose == 1) {
                for (int x = 0; x < n; x++) {
                    for (int y = 0; y < n; y++) {
                        System.out.printf("%.2f%c ", meanValue[x][y], (y < n - 1 ? ',' : ' '));
                    }
                    System.out.println();
                }

            }
            long iterationEnd = System.nanoTime();

            System.out.println("Iteration " + currentIteration + " time=" + ((iterationEnd - iterationStart) / 1E9D) + " sec");
            System.out.println();
        }

        // compute min- and max-value and positions in a and output them

        AtomicInteger minimum = new AtomicInteger((int) (maxPrimePos.get() + 1));
        AtomicInteger maximum = new AtomicInteger(0);
        ArrayList<int[]> minPos = new ArrayList<>();
        ArrayList<int[]> maxPos = new ArrayList<>();

        GlobalRef<AtomicInteger> minRef = new GlobalRef<>(minimum);
        GlobalRef<AtomicInteger> maxRef = new GlobalRef<>(maximum);
        GlobalRef<ArrayList> minPosRef = new GlobalRef<>(minPos);
        GlobalRef<ArrayList> maxPosRef = new GlobalRef<>(maxPos);
        final int nnnPerPlace = n * n * n / p;

        long minMaxStart = System.nanoTime();

        /*finish(() -> {
            for (final Place place : places()) {
                asyncAt(place, () -> {
                    for (int pos = place.id * nnnPerPlace; pos < (place.id + 1) * nnnPerPlace; pos++) {
                        int x = (int) (pos / (n * n));
                        int y = (int) ((pos / n) % n);
                        int z = pos % n;
                        int minVal = at(minRef.home(), () -> minRef.get().get());
                        int maxVal = at(maxRef.home(), () -> maxRef.get().get());
                        if (a[x][y][z] <= minVal) {
                            if (a[x][y][z] < minVal) {
                                at(minPosRef.home(), () -> {
                                    minPosRef.get().clear();
                                });
                            }
                            final int xVal = x;
                            final int yVal = y;
                            final int zVal = z;
                            at(minRef.home(), () -> {
                                minRef.get().set(a[xVal][yVal][zVal]);
                            });
                            ArrayList<Integer> newMinPos = new ArrayList<>();
                            newMinPos.add(x);
                            newMinPos.add(y);
                            newMinPos.add(z);
                            at(minPosRef.home(), () -> {
                                //System.out.println(minPosRef.get());
                                minPosRef.get().add(newMinPos);
                                //System.out.println(minPosRef.get());
                            });
                        }
                        if (a[x][y][z] >= maxVal) {
                            if (a[x][y][z] > maxVal) {
                                at(maxPosRef.home(), () -> maxPosRef.get().clear());
                            }
                            final int xVal = x;
                            final int yVal = y;
                            final int zVal = z;
                            at(maxRef.home(), () -> maxRef.get().set(a[xVal][yVal][zVal]));
                            ArrayList<Integer> newMaxPos = new ArrayList<>();
                            newMaxPos.add(x);
                            newMaxPos.add(y);
                            newMaxPos.add(z);
                            at(maxPosRef.home(), () -> maxPosRef.get().add(newMaxPos));
                        }
                    }
                });
            }
        });*/

        finish(() -> {
            int startVal = 0;
            int placeNum = (places().size() > 1 ? 1 : 0);
            int blockSize = (int) n * n * (n / 2);
            while (startVal < n * n * n) {
                final int thisStartVal = startVal;
                final int thisBlockSize = blockSize;
                asyncAt(place(placeNum), () -> {
                    int minVal = at(minRef.home(), () -> minRef.get().get());
                    int maxVal = at(maxRef.home(), () -> maxRef.get().get());
                    int x, y, z;
                    ArrayList<int[]> myMinPos = new ArrayList<>();
                    ArrayList<int[]> myMaxPos = new ArrayList<>();
                    boolean newMin = false;
                    boolean newMax = false;
                    for (int pos = thisStartVal; pos < (thisStartVal + thisBlockSize) && pos < n * n * n; pos++) {
                        x = (int) (pos / (n * n));
                        y = (int) ((pos / n) % n);
                        z = pos % n;
                        if (a[x][y][z] <= minVal) {
                            if (a[x][y][z] < minVal) {
                                minVal = a[x][y][z];
                                myMinPos.clear();
                            }
                            int[] newPos = {x, y, z};
                            myMinPos.add(newPos);
                            newMin = true;
                        }
                        if (a[x][y][z] >= maxVal) {
                            if (a[x][y][z] > maxVal) {
                                maxVal = a[x][y][z];
                                myMaxPos.clear();
                            }
                            int[] newPos = {x, y, z};
                            myMaxPos.add(newPos);
                            newMax = true;
                        }
                    }
                    if (newMin) {
                        int newMinVal = at(minRef.home(), () -> minRef.get().get());
                        if (minVal == newMinVal) {
                            asyncAt(minPosRef.home(), () -> {
                                for (int[] newPos : myMinPos) {
                                    minPosRef.get().add(newPos);
                                }
                            });
                        } else if (minVal < newMinVal) {
                            final int fmin = minVal;
                            asyncAt(minRef.home(), () -> {
                                minRef.get().getAndSet(fmin);
                            });
                            asyncAt(minPosRef.home(), () -> {
                                minPosRef.get().clear();
                                for (int[] newPos : myMinPos) {
                                    minPosRef.get().add(newPos);
                                }
                            });
                        }
                    }
                    if (newMax) {
                        int newMaxVal = at(maxRef.home(), () -> maxRef.get().get());
                        if (maxVal == newMaxVal) {
                            asyncAt(maxPosRef.home(), () -> {
                                for (int[] newPos : myMaxPos) {
                                    maxPosRef.get().add(newPos);
                                }
                            });
                        } else if (maxVal > newMaxVal) {
                            final int fmax = maxVal;
                            asyncAt(maxRef.home(), () -> {
                                maxRef.get().getAndSet(fmax);
                            });
                            asyncAt(maxPosRef.home(), () -> {
                                maxPosRef.get().clear();
                                for (int[] newPos : myMaxPos) {
                                    maxPosRef.get().add(newPos);
                                }
                            });
                        }
                    }
                });
                startVal += blockSize;
                placeNum = (placeNum + 1) % p;
                if (placeNum == 0) {
                    blockSize *= 1;
                }
            }
        });

        long minMaxEnd = System.nanoTime();
        System.out.println("Min- and Max-Values: time=" + ((minMaxEnd - minMaxStart) / 1E9D) + " sec");
        System.out.println();


        System.out.print("Min=" + minimum + " ");
        for (int[] pos : minPos) {
            System.out.print("(" + pos[0] + ", " + pos[1] + ", " + pos[2] + ") ");
        }
        System.out.println();
        System.out.print("Max=" + maximum + " ");
        for (int[] pos : maxPos) {
            System.out.print("(" + pos[0] + ", " + pos[1] + ", " + pos[2] + ") ");
        }
        System.out.println();

        long end = System.nanoTime();
        System.out.println("Process time=" + ((end - start) / 1E9D) + " sec");
    }

    // with ArrayList and GlobalRef
    public static long findPrimeNumber(int x) {
        int mPP = at(primeRef.home(), () -> primeRef.get().size()) - 1;//maxPrimePosRef.get().get();
        if (mPP < 0) {
            mPP = 0;
        }
        if (x < mPP) {
            //System.out.println("  x=" + x + ": " + primeRef.get());
            return at(primeRef.home(), () -> primeRef.get().get(x));
        }
        int count = mPP;
        long a;
        if (mPP <= 0) {
            a = 2;
            at(primeRef.home(), () -> {
                synchronized (primeRef.get()) {
                    if (primeRef.get().size() <= 0) {
                        primeRef.get().add(0, 0l);
                    }
                }
            });
        } else {
            //System.out.println("mPP=" + mPP + ": " + primeRef.get());
            final int fMPP = mPP;
            a = at(primeRef.home(), () -> primeRef.get().get(fMPP));
            a += 1;
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
                final long prim = a;
                final int c = count;
                at(primeRef.home(), () -> {
                    synchronized (primeRef.get()) {
                        if (primeRef.get().size() <= c) {
                            //System.out.println("c = " + c + ", prim = " + prim);
                            primeRef.get().add(c, prim); // Achtung, hier ist noch ein Sync-Fehler!!!
                        }
                    }
                });
                at(maxPrimePosRef.home(), () -> {
                    maxPrimePosRef.get().addAndGet(1);
                });
            }
            a++;
        }
        return (--a);
    }

    // with ArrayList and GlobalRef -> saving at one point
    /*public static long findPrimeNumber(int x) {
        int mPP = maxPrimePosRef.get().get();
        ArrayList<Long> myPrimes = primeRef.get();
        if (x <= mPP) {
            return myPrimes.get(x);
        }
        int count = mPP;
        long a;
        if (mPP == 0) {
            a = 2;
            myPrimes.add(0, 0l);
        } else {
            a = myPrimes.get(mPP) + 1;
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
                myPrimes.add(count, a);
            }
            a++;
        }
        final int c = count;
        final ArrayList<Long> p = myPrimes;
        asyncAt(primeRef.home(), () -> {
            for (int i = mPP + 1; i <= c; i++) {
                primeRef.get().add(i, p.get(i));
            }
        });
        asyncAt(maxPrimePosRef.home(), () -> {
            maxPrimePosRef.get().getAndSet(c);
        });
        return (--a);
    }*/


}
