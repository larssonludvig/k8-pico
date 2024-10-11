package se.umu.cs.ads.clustermanagement;

import java.util.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.PicoCommunication;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.nodemanager.NodeManager;
public class ClusterManager {
	private final static Logger logger = LogManager.getLogger(ClusterManager.class);
	private final Map<InetSocketAddress, Node> cluster;
	private final PicoCommunication comm;
	private final NodeManager manager;
	public final static String CLUSTER_NAME = "k8-pico";
	
	public ClusterManager(NodeManager manager) {
		this.cluster = new ConcurrentHashMap<>();
		this.manager = manager;
		this.comm = new PicoCommunication(this, manager.getAddress());
	}


	/**
	 * Create a new cluster with us as sole members  
	 */
	public void createCluster() {
		cluster.put(manager.getAddress(), manager.getNode());
	}

	/**
	 * Leave the cluster
	 * @throws PicoException if any erros occurrs
	 */
	public void leaveCluster() throws PicoException {
		Node node = manager.getNode();
		cluster.remove(node.getAddress());
		JMessage leaveReq = new JMessage().setType(MessageType.LEAVE_REQUEST).setPayload(node);
		broadcast(leaveReq);
	}

	public NodeManager getNodeManager() {
		return manager;
	}

	public void joinCluster(InetSocketAddress address) {
		JMessage joinReq = new JMessage()
			.setDestination(address)
			.setType(MessageType.JOIN_REQUEST);
		send(joinReq);
	}

	public List<Node> getClusterMembers() {
		return new ArrayList<Node>(cluster.values());
	}

	public List<InetSocketAddress> getClusterAddresses() {
		return cluster.values().stream().map(it -> it.getAddress()).toList();
	}

	public List<Node> getNodes() {
		return new ArrayList<Node>(cluster.values());
	}

	public void addNode(Node node) {
		cluster.put(node.getAddress(), node);
	}

	public void removeNode(Node node) {
		cluster.remove(node.getAddress());
	}

	public void updateNode(Node node) {
		cluster.put(node.getAddress(), node);
	}

	public Node getNode(InetSocketAddress address) {
		return cluster.get(address);
	}

	public JMessage send(JMessage msg) {
		return comm.sendJMessage(msg);
	}

	public List<JMessage> broadcast(JMessage msg) {
		List<InetSocketAddress> addresses = getNodes().stream().map(it -> it.getAddress()).toList();
		return comm.broadcast(addresses, msg);
	}

	public void receive(JMessage message) {
        manager.receive(message);
    }

    public JMessage join(JMessage msg) {
        // Reliable multicast by passing along if the user is not registered
        // by the current node
        Node node = (Node) msg.getPayload();
        InetSocketAddress adr = node.getAddress();
        
        // Check if joining node is in the cluster
        if (!cluster.containsKey(adr)) {
            return new JMessage()
                .setType(MessageType.ERROR)
                .setPayload("Node already in network");
        }

        this.cluster.put(adr, node);

        broadcast(msg);
        
        return new JMessage()
            .setType(MessageType.EMPTY)
            .setPayload("Successfully joined network.");
    }
}