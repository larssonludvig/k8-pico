package se.umu.cs.ads;

import java.util.ArrayList;

import se.umu.cs.ads.node_manager.Manager;

public class Backend {
    public static void main(String[] args) {
        try {
            Manager man = new Manager();
            man.start("k8-pico", args[0]);

            ArrayList<String> data = new ArrayList<>();
            data.add("testing");
            data.add("Here we go again");
            while(true) {
                man.broadcast(data);
                Thread.sleep(3000);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
