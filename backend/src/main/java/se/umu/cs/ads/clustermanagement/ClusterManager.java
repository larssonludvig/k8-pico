package se.umu.cs.ads.clustermanagement;

import java.util.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.types.PicoAddress;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.communication.PicoCommunication;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.nodemanager.NodeManager;


public class ClusterManager {
	private final static Logger logger = LogManager.getLogger(ClusterManager.class);
	private final Map<PicoAddress, Node> cluster;
	private final Map<PicoAddress, Integer> suspectedMembers;
	private final PicoCommunication comm;
	private final NodeManager manager;
	private final ScheduledExecutorService scheduledPool = CommandLineArguments.scheduledPool;
	public final String CLUSTER_NAME = "k8-pico";
	private final ExecutorService pool;

	public ClusterManager(NodeManager manager) {
		this.cluster = new ConcurrentHashMap<>();
		this.manager = manager;
		this.comm = new PicoCommunication(this, manager);
		this.suspectedMembers = new HashMap<PicoAddress, Integer>();
		this.pool = CommandLineArguments.pool;

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

	public PicoCommunication getCommunication() {
		return this.comm;
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

	public void heartbeat() {
		List<Node> members = getClusterMembers();

		for (Node node : members) {
			pool.submit(() -> {
				try {
					// Send heartbeat to node
					Node n = this.comm.heartbeatRemote(node.getAddress());

					// Update node data in cluster
					cluster.put(n.getAddress(), n);

					// Remove node from suspected list
					suspectedMembers.remove(node.getAddress());
				} catch (PicoException e) {
					logger.warn("Failed to send heartbeat to {}: {}", node.getAddress(), e.getMessage());
					logger.debug("Found suspected dead node {} adding or incrementing list of suspects.", node.getAddress());
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
			});
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
				logger.debug("Failed to get ISSUSPECT from {}", node.getAddress());
				// We do not need to handle this since it is already checking for dead nodes
			}
		}

		return true;
	}

	public boolean isSuspect(PicoAddress adr) {
		return suspectedMembers.containsKey(adr);
	}

	public void createContainer(PicoContainer container) {
		PicoAddress leader = getLeader();
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

	public List<PicoContainer> getContainers(PicoAddress adr) {
		Node n = this.cluster.get(adr);
		return n.getContainers();
	}

	public PicoContainer getContainer(String name) {
		List<Node> nodes = new ArrayList<Node>(cluster.values());
		if (!nodes.contains(manager.getNode()))
			nodes.add(manager.getNode());

		for (Node node : cluster.values()) {
			for (PicoContainer container : node.getContainers()) {
				if (container.getName().equals(name)) {
					return container;
				}
			}
		}
		return null;
	}
}