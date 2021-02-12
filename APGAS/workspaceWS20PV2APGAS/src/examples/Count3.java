package examples;

import apgas.Configuration;
import apgas.Place;
import apgas.util.GlobalRef;

import java.util.concurrent.atomic.AtomicInteger;

import static apgas.Constructs.*;

public class Count3 {
    public static void main(String[] args) {
        final int n = Integer.parseInt(args[0]);
        Configuration.APGAS_PLACES.setDefaultValue(Integer.parseInt(args[1]));
        final int p = places().size();
        Configuration.APGAS_THREADS.setDefaultValue(Integer.parseInt(args[2]));
        final int t = Configuration.APGAS_THREADS.get();

        if (n % (p * t) != 0 || n < p * t) {
            System.out.println("n muss Vielfaches von pt sein");
            System.exit(1);
        }

        // Initialisierung
        long start = System.nanoTime();

        final GlobalRef<Integer[]> gA = new GlobalRef<>(
                places(), () -> {
            Integer[] myA = new Integer[n / p];
            finish(() -> {
                int index = n / (p * t);
                for (int k = 0; k < t; k++) {
                    final int fk = k;
                    async(() -> {
                        for (int i = 0; i < index; i++) {
                            int value = i + (fk * index) + (here().id * (n / p));
                            myA[i + (fk * index)] = (int) Math.tan(value);
                        }
                    });
                }
            });
            return myA;
        });
        long end = System.nanoTime();
        System.out.println("Time Init: " + ((end - start) / 1E9D) + " sec");

        // Berechnung
        final GlobalRef<AtomicInteger> gSum = new GlobalRef<>(new AtomicInteger(0));

        finish(() -> {
            for (final Place place : places()) {
                asyncAt(place, () -> {
                    int mySum = 0;
                    Integer[] myA = gA.get();
                    for (int i = 0; i < n / p; ++i) {
                        if (myA[i] == 3) {
                            mySum++;
                        }
                    }
                    final int myFinalSum = mySum;
                    asyncAt(gSum.home(), () -> {
                        gSum.get().addAndGet(myFinalSum);
                    });
                });
            }
        });
        end = System.nanoTime();
        System.out.println("Sum=" + gSum.get());
        System.out.println("Process time=" + ((end - start) / 1E9D) + " sec");
    }
}
