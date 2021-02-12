package examples;

import apgas.Configuration;
import apgas.Place;
import apgas.util.GlobalRef;

import static apgas.Constructs.*;

// entfernter Datenzugriff
public class RectTest {
    public static void main(String[] args) {
        Configuration.APGAS_PLACES.setDefaultValue(4);

        // Lesezugriff
        final Rectangle r = new Rectangle(3,3);
        final int rHeight = r.getHeight();
        for (final Place place : places()) {
            at(place, () -> {
                System.out.println(here() + "height = " + rHeight);
                r.incHeight();
            });
        }

        // Schreibzugriff
        final GlobalRef<Rectangle> gr = new GlobalRef<>(new Rectangle(3,3));
        for(final Place place : places()) {
            at(place, () -> {
                System.out.println(here() + " height = " + at(gr.home(), () -> {
                    int thatHeight = gr.get().getHeight();
                    gr.get().incHeight();
                    return thatHeight;
                }));
            });
        }
        System.out.println(here() + " height = " + gr.get().getHeight());

        // GlobalRef für int
        final GlobalRef<Integer> grI = new GlobalRef<>(7);
        System.out.println(gr.home() + ": " + grI.get());

        at(place(1), () -> {
            System.out.println(here() + ", " + gr.home());
            at(grI.home(), () -> {
                grI.set(grI.get() + 1);
            });
        });
        System.out.println(grI.home() + ": " + grI.get());
    }
}
