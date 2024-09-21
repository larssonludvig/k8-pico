package se.umu.cs.ads;

import java.sql.SQLOutput;
import java.util.ArrayList;

import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.podengine.PodEngine;
import se.umu.cs.ads.types.*;

public class Backend {
    public static void main(String[] args) {
        try {
            NodeManager man = new NodeManager();
            man.start("k8-pico", args[0]);






            if (args[0].equals("node2")) {
                while (true) {
                    System.out.print("<image name> <container name>: ");
                    String line = System.console().readLine();

                    if (line.equals("quit"))
                        break;

                    String imgName = line.split(" ")[0];
                    String contName = line.split(" ")[1];

                    JMessage message = new JMessage(
                            MessageType.CREATE_CONTAINER,
                            new String[]{imgName, contName}
                    );

                    man.broadcast(message);
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
