package se.umu.cs.ads.nodemanager;

import java.util.List;
import java.util.Optional;
import java.lang.IllegalArgumentException;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.BytesMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.MessageDispatcher;

import se.umu.cs.ads.types.JMessage;
import se.umu.cs.ads.types.MessageType;
import se.umu.cs.ads.types.Node;

/**
 * Class for cluster management
 */
public class NodeManager {
    private NodeDispatcher disp = null;
    private JChannel ch = null;
    private Node node = null;

    // Node information management -------------------------------------------------

    public NodeManager() {
        this.node = new Node();
    }

    public NodeManager(String name, String ip, String port, String cluster) {
        this.node = new Node(name, ip, port, cluster);
    }

    public Node getNode() {
        return this.node;
    }

    public List<Node> getNodes() {
        try {
            JMessage msg = new JMessage(
                MessageType.FETCH_NODES,
                "placeholder"
            );

            List<Node> nodes = broadcast(msg).stream()
                .map(obj -> (Node) obj)
                .toList();
            
            // nodes.add(this.node);
            return nodes;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Cluster and channel management ----------------------------------------------
    
    /**
     * Create or join a cluster by predefined node values
     * @throws Exception IllegalArgumentException
     */
    public void start() throws Exception {
        String cluster = this.node.getCluster();
        String name = this.node.getName();

        start(cluster, name);
    }

    /**
     * Create or join cluster by paramiters
     * @param cluster Name of cluster to join
     * @param node Name of this node
     * @throws Exception IllegalArgumentException
     */
    public void start(String cluster, String node) throws Exception {
        if (node == null || node.equals("") || cluster == null || cluster.equals(""))
            throw new IllegalArgumentException("No name or clurster defined for node.");

        this.node.setName(node);
        this.node.setCluster(cluster);

        this.ch = new JChannel()
            .name(node)
            // .setDiscardOwnMessages(true)
            .setReceiver(new CustomReceiver(node));

        NodeDispatcher nDisp = new NodeDispatcher();
        this.disp = nDisp.initialize(
            new MessageDispatcher(this.ch, nDisp),
            this
        );

        this.ch.connect(cluster);
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
    public List<Object> broadcast(Object obj) throws Exception {
        return this.disp.broadcast(obj);
    }

    /**
     * Send a message to a specific node
     * @param dest Node to send to
     * @param obj Object to send
     * @throws Exception exception
     */
    public Object send(Address dest, Object obj) throws Exception {
        return this.disp.send(dest, obj);
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
         * Override viewAccepted of Receiver
         * @param v Current cluster views
         */
        @Override
        public void viewAccepted(View v) {
            System.out.printf("-- [%s] new view: %s\n", name, v);
        }
    }
}