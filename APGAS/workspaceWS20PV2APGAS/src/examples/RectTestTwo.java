package examples;

import apgas.Configuration;
import apgas.util.PlaceLocalObject;

import static apgas.Constructs.*;

final class RectTestTwo extends PlaceLocalObject {


    Rectangle r;

    public RectTestTwo(int first, int second) {
        this.r = new Rectangle(first, second);
    }

    public static void main(String[] args) {
        Configuration.APGAS_PLACES.setDefaultValue(2);

        final RectTestTwo rectTestTwo = RectTestTwo.make(places(), () -> new RectTestTwo(here().id, here().id));

        finish(() -> {
            asyncAt(place(1), () -> {
                System.out.println(here() + " height = " + rectTestTwo.r.getHeight());
                asyncAt(place(0), () -> {
                    System.out.println(here() + " height = " + rectTestTwo.r.getHeight());
                    rectTestTwo.r.incHeight();
                });
            });
        });
        System.out.println(here() + " height = " + rectTestTwo.r.getHeight());
    }
}
