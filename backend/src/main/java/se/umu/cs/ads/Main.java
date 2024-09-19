package se.umu.cs.ads;

import se.umu.cs.ads.podengine.PodEngine;

import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        PodEngine engine = new PodEngine();

        if (args.length < 2) {
            System.err.println("Usage: <img name> <container name>");
        }

        String imgName = args[0];
        String contName = args[1];
        engine.refreshContainers();
        engine.createContainer(imgName, contName);
        engine.runContainer(contName);

        engine.restartContainer(contName);


        List<String> logs = engine.containerLog(contName);
        System.out.println("Number of entries: " + logs.size());

        engine.removeContainer(contName);
    }
}
