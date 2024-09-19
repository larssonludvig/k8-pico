package se.umu.cs.ads;

import java.sql.SQLOutput;
import java.util.ArrayList;

import se.umu.cs.ads.node_manager.Manager;
import se.umu.cs.ads.podengine.PodEngine;

public class Backend {
    public static void main(String[] args) {
        try {
            Manager man = new Manager();
            man.start("k8-pico", args[0]);






            if (args[0].equals("node2")) {
                while (true) {
                    System.out.print("<image name> <container name>: ");
                    String line = System.console().readLine();

                    if (line.equals("quit"))
                        break;

                    String imgName = line.split(" ")[0];
                    String contName = line.split(" ")[1];
                    ArrayList<String> data = new ArrayList<>();

                    data.add(imgName);
                    data.add(contName);
                    man.broadcast(data);


                }
            }

//            engine.runContainer(imgName, contName);
//            while(true) {
//                man.broadcast(data);
//                Thread.sleep(3000);
//            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
