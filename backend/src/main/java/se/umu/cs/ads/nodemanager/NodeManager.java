package se.umu.cs.ads.nodemanager;

import java.util.List;
import java.util.Optional;
import java.lang.IllegalArgumentException;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.MessageDispatcher;

import se.umu.cs.ads.controller.Controller;
import se.umu.cs.ads.types.JMessage;
import se.umu.cs.ads.types.MessageType;
import se.umu.cs.ads.types.Node;
import se.umu.cs.ads.types.Pod;

/**
 * Class for cluster management
 */
public class NodeManager {
    private NodeDispatcher disp = null;
    private JChannel ch = null;
    public Node node = null;
	private final Controller controller;

    public NodeManager(Controller controller) {
        this.node = new Node();
		this.controller = controller;
    }

    // Node information management -------------------------------------------------

    public Node getNode() {
        return this.node;
    }

    public Node getNode(String nodeName) throws Exception {
        if (nodeName.equals(this.node.getName())) {
            return this.node;
        } else {

            Address address = getAddressOfNode(nodeName);
            if (address == null) {
                throw new Exception("Unable to fetch node, not a member of the cluster.");
            }

            JMessage msg = new JMessage(
                MessageType.FETCH_NODE,
                nodeName
            );

            Object result = send(address, msg);
            if (!(result instanceof Node)) {
                throw new Exception("Fetched object is not of type Node.");
            }

            return (Node) result;
        }
    }

    public List<Node> getNodes() throws Exception {
        JMessage msg = new JMessage(
            MessageType.FETCH_NODES,
            ""
        );

        return broadcast(msg).stream()
            .map(obj -> (Node) obj)
            .toList();
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

		this.node.setAddress(ch.getAddressAsString());
		this.node.setPort(controller.getPort());
        NodeDispatcher nDisp = new NodeDispatcher();
        this.disp = nDisp.initialize(
            new MessageDispatcher(this.ch, nDisp),
            this
        );

        this.ch.connect(cluster);
    }

	public void stop() {
		this.ch.disconnect();
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