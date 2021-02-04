package examples;

import apgas.Configuration;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static apgas.Constructs.async;
import static apgas.Constructs.finish;

public class PiParallel {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage Pi <N>");
            System.exit(1);
        }
        final long n = Long.parseLong(args[0]);

        piSequential(n);
        piParallel(n);
    }

    public static void piSequential(long n) {
        long start = System.nanoTime();
        int result = 0;
        final Random rand = new Random();
        for (int i = 0; i < n; i++) {
            final double x = rand.nextDouble();
            final double y = rand.nextDouble();
            if (x * x + y * y <= 1) {
                result++;
            }
        }
        final double pi = 4.0 * result / n;
        long end = System.nanoTime();
        System.out.println("Pi = " + pi);
        System.out.println("Calculated with n: " + n);
        System.out.println("Time in s: " + ((end - start) / 1E9D));
    }

    public static void piParallel(long n) {
        long start = System.nanoTime();
        final int t = Configuration.APGAS_THREADS.get();
        System.out.println("t = " + t);
        final long nPerWorker = n / t;
        if (n % (t) != 0 || n < t) {
            System.out.println("n must be a multiple of t");
            System.exit(1);
        }
        final AtomicInteger result = new AtomicInteger();
        finish(() -> {
            for (int i = 0; i < t; i++) {
                async(() -> {
                    int myresult = 0;
                    final Random rand = new Random();
                    for (int j = 0; j < nPerWorker; j++) {
                        final double x = rand.nextDouble();
                        final double y = rand.nextDouble();
                        if (x * x + y * y <= 1) {
                            myresult++;
                        }
                    }
                    result.addAndGet(myresult);
                });
            }
        });
        final double pi = 4.0 * result.get() / n;
        long end = System.nanoTime();
        System.out.println("Pi = " + pi);
        System.out.println("Calculated with n: " + n);
        System.out.println("Time in s: " + ((end - start) / 1E9D));
    }
}
