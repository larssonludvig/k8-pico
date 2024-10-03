package se.umu.cs.ads.nodemanager;

import java.util.*;
import java.util.Optional;
import java.lang.IllegalArgumentException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.logging.log4j.LogManager;
import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.PhysicalAddress;
import se.umu.cs.ads.controller.Controller;
import se.umu.cs.ads.types.*;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class for cluster management
 */
public class NodeManager {
    private NodeDispatcher disp = null;
    private JChannel ch = null;
    public Node node = null;
	private final Controller controller;
	private AtomicReference<View> currentView;

	private final static Logger logger = LogManager.getLogger(NodeManager.class);
    public NodeManager(Controller controller, String cluster) {
        this.node = new Node(cluster);
		this.controller = controller;
		this.currentView = new AtomicReference<>();
		this.currentView.set(new View());
    }

    // Node information management -------------------------------------------------

    public Node getNode() {
        return this.node;
    }

	public synchronized void refreshView() {
		View currentView = this.currentView.get();
		View newView = this.ch.getView();
		List<Address> newMembers = View.newMembers(currentView, newView);
		List<Address> deadMembers = View.leftMembers(currentView, newView);
		if (newMembers.size() > 0) 
			System.out.println("New members: " + newMembers.get(0).toString());

		if (deadMembers.size() > 0)
			System.out.println("Dead members: " + deadMembers.get(0).toString());
		
		this.currentView.set(newView);
		System.out.println("Current leader: " + getLeader());
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

	public synchronized void setActiveContainers(List<PicoContainer> containers) {
		this.node.setContainers(containers);
	}

    // Cluster and channel management ----------------------------------------------
    
	public Address getLeader() {
			refreshView();
			return this.currentView.get().getMembers().get(0);
		}

    /**
     * Create or join cluster by paramiters
     * @param cluster Name of cluster to join
     * @param node Name of this node
     * @throws Exception IllegalArgumentException
     */
    public void start() throws Exception {
       

		this.node.setName(InetAddress.getLocalHost().getHostName());

		String cluster = this.node.getCluster();
        this.ch = new JChannel()
            .name(node.getName())
            // .setDiscardOwnMessages(true)
            .setReceiver(new CustomReceiver(node.getName(), currentView));

        NodeDispatcher nDisp = new NodeDispatcher();
        this.disp = nDisp.initialize(
            new MessageDispatcher(this.ch, nDisp),
            this
        );

        this.ch.connect(cluster);
		this.currentView.set(ch.getView());
		this.node.setAddress(getAddress());
		logger.info("Node: {}", this.node);
	}


	private String getAddress() {
		if (this.ch == null)
			throw new IllegalStateException("Cannot determine address if channel is not created yet.");

			Object o = this.ch.down(new Event(Event.GET_PHYSICAL_ADDRESS, this.ch.getAddress()));

			if (!(o instanceof PhysicalAddress))
				throw new IllegalStateException("Cannot determine local address");
			
			PhysicalAddress address = (PhysicalAddress) o;
			return address.printIpAddress();
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
		protected final AtomicReference<View> viewUpdater;
		private static final Logger logger = LogManager.getLogger(CustomReceiver.class);
        /**
         * Custom receiver constructor
         * @param name Name of current node
         */
        protected CustomReceiver(String name, AtomicReference<View> viewUpdater) {
            this.name = name;
			this.viewUpdater = viewUpdater;
        }

        /**
         * Override viewAccepted of Receiver
         * @param v Current cluster views
         */
        // @Override
        public void viewAccepted(View v) {
			viewUpdater.set(v);
		    logger.info("-- [%s] new view: %s\n", name, v);
			logger.error("New leader: " + v.getMembers().get(0));
        }
    }
}