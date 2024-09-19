package se.umu.cs.ads.node_manager;

import java.util.ArrayList;
import java.util.Optional;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import se.umu.cs.ads.podengine.Pod;
import se.umu.cs.ads.podengine.PodEngine;

/**
 * Class for cluster management
 */
public class Manager {
    private JChannel ch = null;
    
    /**
     * Create or join cluster by paramiters
     * @param cluster Name of cluster to join
     * @param node Name of this node
     * @throws Exception exception
     */
    public void start(String cluster, String node) throws Exception {
        ch = new JChannel("udp.xml")
            .name(node)
            .setDiscardOwnMessages(true)
            .setReceiver(new CustomReceiver(node))
            .connect(cluster);
    }

    /**
     * Finds the address of a node by name
     * @param node Name of node to get address from
     * @return Found address of node
     */
    public Address getAddressOfNode(String node)  {
        Optional<Address> optDest = ch.view().getMembers().stream()
            .filter(address -> node.equals(address.toString()))
            .findAny();

        return optDest.orElse(null);
    }

    /**
     * Broadcast a message over the cluster
     * @param obj Object to broadcast
     * @throws Exception exception
     */
    public void broadcast(Object obj) throws Exception {
        Message msg = new Message(null, obj);
        
        ch.send(msg);
    }

    /**
     * Send a message to a specific node
     * @param dest Node to send to
     * @param obj Object to send
     * @throws Exception exception
     */
    public void send(Address dest, Object obj) throws Exception {
        Message msg = new Message(dest, obj);
        
        ch.send(msg);
    }

    /**
     * Custom receiver class that implements the JGroups Receiver
     */
    protected static class CustomReceiver implements Receiver {
        protected final String name;

        /**
         * Custom receiver constructor
         * @param name Name of current node
         */
        protected CustomReceiver(String name) {
            this.name = name;
        }

        /**
         * Override receive method of Receiver
         * @param msg
         */
        @Override
        public void receive(Message msg) {
            ArrayList<String> obj = msg.getObject();
            String imgName = obj.get(0);
            String contName = obj.get(1);

            PodEngine engine = new PodEngine();
            try {
                Pod pod = engine.createContainer(imgName, contName);
                pod = engine.runContainer(contName);
                String id = pod.getId();
                System.out.println("Container started sucessfully!");
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }

        /**
         * Override viewAccepted of Receiver
         * @param v Current cluster views
         */
        @Override
        public void viewAccepted(View v) {
            System.out.printf("-- [%s] new view: %s\n", name, v);
        }
    }
}