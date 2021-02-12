package group2;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class Squares_sequential {

    //private static long[] primes;
    private static int maxPrimePos;
    private static ArrayList<Long> primes;

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
        maxPrimePos = 0;

        int min;
        int max;
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
            long zValStart = System.nanoTime();
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    //zValue[x][y] = 0;
                    for (int j = 0; j < n; j++) {
                        zValue[x][y] += findPrimeNumber(a[x][y][j]);
                    }
                }
            }
            long zValEnd = System.nanoTime();
            System.out.println("zValue : time=" + ((zValEnd - zValStart) / 1E9D) + " sec");

            System.out.println("zValues: ");
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    System.out.printf("%d%c ", zValue[x][y], (y < n - 1 ? ',' : ' '));
                }
                System.out.println();
            }

            // compute meanValue
            long meanValStart = System.nanoTime();
            for (int x = 0; x < n; x++) {
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
            }
            long meanValEnd = System.nanoTime();
            System.out.println("meanValue : time=" + ((meanValEnd - meanValStart) / 1E9D) + " sec");

            // compute new a array
            long aStart = System.nanoTime();
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    for (int z = 0; z < n; z++) {
                        random.setSeed((long) meanValue[x][y] + z);
                        // currentIteration is in range of 0..(i-1)
                        int bound = m + (int) (meanValue[x][y] / ((currentIteration + 1) * 50));
                        a[x][y][z] = random.nextInt(bound) + 1;
                    }
                }
            }
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

        // To Do: compute min- and max-value and positions in a and output them

        long minMaxStart = System.nanoTime();

        min = (int) (primes.get(maxPrimePos) + 1);
        max = 0;

        for (int x = 0; x < n; x++) {
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
        }
        long minMaxEnd = System.nanoTime();
        System.out.println("Min- and Max-Values: time=" + ((minMaxEnd - minMaxStart) / 1E9D) + " sec");
        System.out.println();

        System.out.println("Min=" + min + " " + minPos);
        System.out.println("Max=" + max + " " + maxPos);

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
    public static long findPrimeNumber(int x) {
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
    }

    // with ArrayList and GlobalRef
    /*public static long findPrimeNumber(int x) {
        if (x <= maxPrimePosRef.get()) {
            return primeRef.get().get(x);
        }
        int count = maxPrimePosRef.get();
        long a;
        if (maxPrimePosRef.get() == 0) {
            a = 2;
            primeRef.get().add(0, 0l);
        } else {
            a = primeRef.get().get(maxPrimePosRef.get()) + 1;
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
                maxPrimePosRef.set(maxPrimePosRef.get() + 1);
            }
            a++;
        }
        return (--a);
    }*/

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