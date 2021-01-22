package src;

import java.util.Locale;
import java.util.Random;

public class Squares{
    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);

        int n = Integer.parseInt(args[0]);
        int m = Integer.parseInt(args[1]);
        long seedA = Integer.parseInt(args[2]);
        int d = Integer.parseInt(args[3]);
        int i = Integer.parseInt(args[4]);
        int verbose = Integer.parseInt(args[5]);

        int[][][] a = new int[n][n][n];
        int[][] zValue = new int[n][n];
        double[][] meanValue = new double[n][n];

        int min;
        int max;
        int[] minPos = new int[3];
        int[] maxPos = new int[3];

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
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    zValue[x][y] = 0;
                    for (int j = 0; j < n; j++) {
                        zValue[x][y] += findPrimeNumber(a[x][y][j]);
                    }
                }
            }

            // compute meanValue
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

            // compute new a array
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

            // output of meanValue - matrix
            if (verbose == 1) {
                for (int x = 0; x < n; x++) {
                    for (int y = 0; y < n; y++) {
                        System.out.print(String.format("%.2f%c ", meanValue[x][y], (y < n-1 ? ',' : ' ')));
                    }
                    System.out.println();
                }

                long iterationEnd = System.nanoTime();

                System.out.println("Iteration " + currentIteration + " time=" + ((iterationEnd - iterationStart) / 1E9D) + " sec");
                System.out.println();
            }
        }

        // To Do: compute min- and max-value of a and output them

        long end = System.nanoTime();

        System.out.println("Process time=" + ((end - start) / 1E9D) + " sec");
    }

    public static long findPrimeNumber(int x) {
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
    }
}