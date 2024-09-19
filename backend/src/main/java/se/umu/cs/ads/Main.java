package se.umu.cs.ads;

import se.umu.cs.ads.podengine.PodEngine;

import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        PodEngine engine = new PodEngine();

        if (args.length < 2) {
            System.err.println("Usage: <img name> <container name>");
        }

        engine.refreshContainers();
        engine.runContainer(args[0], args[1]);
        List<String> logs = engine.containerLog(args[1]);

        System.out.println(String.join("\n", logs));
        System.out.println("Number of entries: " + logs.size());
    }
}
