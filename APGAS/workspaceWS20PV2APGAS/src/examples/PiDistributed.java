package examples;

import apgas.Configuration;
import apgas.Place;
import apgas.util.GlobalRef;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static apgas.Constructs.*;

public class PiDistributed {
    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Usage Pi <N>");
            System.exit(1);
        }
        final long n = Long.parseLong(args[0]);

        Configuration.APGAS_PLACES.set(4);
        Configuration.APGAS_THREADS.set(4);

        long start = System.nanoTime();

        final int p = places().size();
        final long nPerPlace = n / p;

        GlobalRef<AtomicLong> result = new GlobalRef<>(new AtomicLong());

        finish(() -> {
            for (final Place place: places()) {
                asyncAt(place, () -> {
                    long myResult = 0;
                    for (int i = 0; i < nPerPlace; i++) {
                        final double x = ThreadLocalRandom.current().nextDouble();
                        final double y = ThreadLocalRandom.current().nextDouble();
                        if (x*x+y*y<=1) {
                            myResult++;
                        }
                    }
                    final long remoteResult = myResult;
                    asyncAt(result.home(), () -> {
                        result.get().addAndGet(remoteResult);
                    });
                });
            }
        });
        final double pi = 4.0 * result.get().get() / n;
        long end = System.nanoTime();
        System.out.println("Pi = " + pi);
        System.out.println("Calculated with n: " + n);
        System.out.println("Time in s: " + ((end - start) / 1E9D));
    }
}
