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
	private final Map<PicoAddress, Integer> suspectedMembers;
	private final PicoCommunication comm;
	private final NodeManager manager;
	public final String CLUSTER_NAME = "k8-pico";

	public ClusterManager(NodeManager manager) {
		this.cluster = new ConcurrentHashMap<>();
		this.manager = manager;
		this.comm = new PicoCommunication(this, manager);
		this.suspectedMembers = new HashMap<PicoAddress, Integer>();
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
		logger.info("Cluster size: " + cluster.size());
		return new ArrayList<Node>(cluster.values());
	}

	public void addNode(Node node) {
		cluster.put(node.getAddress(), node);
		suspectedMembers.remove(node.getAddress());
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

	public void heartBeat(PicoAddress adr) {
		List<Node> members = getClusterMembers();

		for (Node node : members) {
			try {
				// Send heartbeat to node
				this.comm.heartBeatRemote(node.getAddress());

				// Remove node from suspected list
				suspectedMembers.remove(node.getAddress());
			} catch (PicoException e) {
				// Handle heartbeat failure
				if (suspectedMembers.containsKey(node.getAddress())) {
					int count = suspectedMembers.get(node.getAddress());
					// Ask nodes if node is suspected for them
					if (count >= 3 && isNodeDead(node.getAddress())) {
						// If it is suspedted by all nodes, remove it
						this.comm.removeNodeRemote(node.getAddress());
					} else {
						suspectedMembers.put(node.getAddress(), count + 1);
					}
				} else {
					suspectedMembers.put(node.getAddress(), 1);
				}
			}
		}
	}

	private boolean isNodeDead(PicoAddress adr) {
		List<Node> members = getClusterMembers();

		for (Node node : members) {
			try {
				if (!adr.equals(node.getAddress()) && !this.comm.isSuspectRemote(node.getAddress(), adr)) {
					return false;
				}
				
			} catch (Exception e) {
				logger.error("Failed to get ISSUSPECT from {}", node.getAddress());
				// We do not need to handle this since it is already checking for dead nodes
			}
		}

		return true;
	}

	public boolean isSuspect(PicoAddress adr) {
		return suspectedMembers.containsKey(adr);
	}

	public void createContainer(PicoContainer container) {
		PicoAddress leader = manager.getLeader();
		this.comm.initiateContainerElection(container, leader);
	}
}