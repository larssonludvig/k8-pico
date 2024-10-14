package se.umu.cs.ads.clustermanagement;

import java.util.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.types.PicoAddress;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.PicoCommunication;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.nodemanager.NodeManager;

import se.umu.cs.ads.communication.*;

public class ClusterManager {
	private final static Logger logger = LogManager.getLogger(ClusterManager.class);
	private final Map<PicoAddress, Node> cluster;
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
	 * 
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

	public void joinCluster(PicoAddress address) throws PicoException {
		Node self = manager.getNode();

		// send join req and add members
		List<Node> newMembers = this.comm.joinRequest(address, self);

		for (Node node : newMembers)
			cluster.put(node.getAddress(), node);
	}

	public List<Node> getClusterMembers() {
		return new ArrayList<Node>(cluster.values());
	}

	public List<PicoAddress> getClusterAddresses() {
		return cluster.values().stream().map(it -> it.getAddress()).toList();
	}

	public List<Node> getNodes() {
		logger.info("Cluster size: " + cluster.size());
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

	public Node getNode(PicoAddress address) {
		return cluster.get(address);
	}

	public JMessage send(JMessage msg) {
		// return comm.sendJMessage(msg);
		return null;
	}

	public List<JMessage> broadcast(JMessage msg) {
		// List<PicoAddress> addresses = new
		// ArrayList<>(getNodes().stream().map(it -> it.getAddress()).toList());
		// if (msg.getType() == MessageType.JOIN_REQUEST || msg.getType() ==
		// MessageType.LEAVE_REQUEST)
		// addresses.remove(manager.getAddress());

		// return comm.broadcast(addresses, msg);
		return new ArrayList<>();
	}

	public void receive(JMessage message) {
		manager.receive(message);
	}

	public List<Node> join(Node node) {
		// Reliable multicast by passing along if the user is not registered
		// by the current node
		PicoAddress adr = node.getAddress();

		List<Node> members = cluster.values().stream().toList();
		// Check if joining node is in the cluster
		if (!cluster.containsKey(adr)) {
			return members;
		}

		cluster.put(adr, node);

		// broadcast(msg);
		return members;
	}
}