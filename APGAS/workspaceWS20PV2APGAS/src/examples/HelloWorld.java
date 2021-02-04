package examples;

import static apgas.Constructs.asyncAt;
import static apgas.Constructs.finish;
import static apgas.Constructs.here;
import static apgas.Constructs.places;

import apgas.Configuration;
import apgas.Place;

final class HelloWorld {

  public static void main(String[] args) {

    //checks whether "-Dapgas.apgas.places=x" was set
    Configuration.APGAS_PLACES.setDefaultValue(4);

    //checks whether "-Dapgas.apgas.threads=y" was set
    Configuration.APGAS_THREADS.setDefaultValue(4);

    Configuration.printAllConfigs();

    System.out.println("Running HelloWorld with " + places().size() + " places, each with "
        + Configuration.APGAS_THREADS.get() + " Threads");

    finish(() -> {
      for (final Place p : places()) {
        asyncAt(p, () -> {
          System.out.println("Hello from " + here());
        });
      }
    });

    System.out.println("Bye from " + here());
  }
}
