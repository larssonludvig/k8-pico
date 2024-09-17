package se.umu.cs.ads;

import se.umu.cs.ads.podengine.PodEngine;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello there!");
        PodEngine engine = new PodEngine();

        for (int i = 0; i < 4; i++) {
            engine.runContainer("nginx:alpine", "test-" + i);
        }

        System.out.println("Containers started!");
        Thread.sleep(10_000);

        for (int i = 0; i < 4; i++) {
            engine.stopContainer( "test-" + i);
        }
        System.out.println("Containers stopped!");
    }
}
