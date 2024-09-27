package se.umu.cs.ads;

import java.sql.SQLOutput;
import java.util.List;
import java.util.ArrayList;

import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.podengine.PodEngine;
import se.umu.cs.ads.types.*;

import org.springframework.boot.SpringApplication;
import se.umu.cs.ads.service.RESTManager;

public class Backend {
    public static void main(String[] args) {
        try {
            SpringApplication.run(RESTManager.class, args);

            // NodeManager man = new NodeManager();
            // man.start("k8-pico", args[0]);

            // while (true) {
            //     System.out.print("Want cluster information?");
            //     String line = System.console().readLine();

            //     if (line.equals("quit")) {
            //         break;
            //     } else {
            //         for (Node node : man.getNodes()) {
            //             System.out.println(node.getName());
            //         }
            //     }
            // }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
