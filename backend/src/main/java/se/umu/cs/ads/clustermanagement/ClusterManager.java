package se.umu.cs.ads.clustermanagement;

import java.util.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.types.*;
import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.communication.PicoCommunication;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.nodemanager.NodeManager;


public class ClusterManager {
	private final static Logger logger = LogManager.getLogger(ClusterManager.class);
	private final Map<PicoAddress, Node> cluster;
	private final PicoCommunication comm;
	private final NodeManager manager;
	private final ScheduledExecutorService scheduledPool = CommandLineArguments.scheduledPool;
	public final String CLUSTER_NAME = "k8-pico";

	public ClusterManager(NodeManager manager) {
		this.cluster = new ConcurrentHashMap<>();
		this.manager = manager;
		this.comm = new PicoCommunication(this, manager);

		scheduledPool.scheduleAtFixedRate(() -> {
			PicoAddress leader = getLeader();
			int members = cluster.values().size();
			logger.debug("Cluster {} has {} members with leader: {}", CLUSTER_NAME, members, leader);
		}, 5, 5, TimeUnit.SECONDS); 
	}

	/**
	 * Create a new cluster with us as sole members
	 */
	public void createCluster() {
		cluster.put(manager.getAddress(), manager.getNode());
	}

	public NodeManager getNodeManager() {
		return manager;
	}

	public PicoAddress getLeader() {
		List<PicoAddress> addresses = getClusterAddresses();
		Collections.sort(addresses);
		if (addresses.isEmpty())
			return manager.getAddress();
			
		return addresses.get(0);
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
		List<PicoAddress> res = cluster.values().stream().map(it -> it.getAddress()).toList();
		return new ArrayList<>(res);
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

	public void removeNode(PicoAddress adr) {
		cluster.remove(adr);
	}

	public void leaveRemote(PicoAddress adr) {
		comm.leaveRemote(adr);
	}

	public void updateNode(Node node) {
		cluster.put(node.getAddress(), node);
	}

	public Node getNode(PicoAddress address) {
		return cluster.get(address);
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

	public Node fetchNode() {
		return this.manager.getNode();
	}

	public Node fetchNode(PicoAddress adr) {
		return this.comm.fetchNode(adr);
	}

	public Performance fetchNodePerformance() {
		return this.manager.getNodePerformance();
	}

	public Performance fetchNodePerformance(PicoAddress adr) {
		return this.comm.fetchNodePerformance(adr);
	}

	public void createContainer(PicoContainer container) {
		PicoAddress leader = manager.getClusterManager().getLeader();
		this.comm.initiateContainerElection(container, leader);
	}

	public void addContainerToNode(PicoAddress address, PicoContainer container) {
		Node n = cluster.get(address);
		if (n == null) {
			logger.warn("Cannot add container to node: No node with address {} in cluster", address);
			return;
		}
		n.addContainer(container);
	}
}