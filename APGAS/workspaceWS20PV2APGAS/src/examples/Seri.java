package examples;

import apgas.Configuration;

import static apgas.Constructs.*;

public class Seri {
    public static void main(String[] args) {
        Configuration.APGAS_PLACES.setDefaultValue(4);

        Example example = new Example(1);
        System.out.println("t = " + Configuration.APGAS_THREADS.get());
        System.out.println("p = " + Configuration.APGAS_PLACES.get());

        finish(() -> {
            asyncAt(place(1), () -> {
                System.out.println(example.getX());
            });
        });
    }
}
