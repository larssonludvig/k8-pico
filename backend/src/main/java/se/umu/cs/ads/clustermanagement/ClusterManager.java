package se.umu.cs.ads.clustermanagement;

import java.util.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.PicoCommunication;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.nodemanager.NodeManager;

/**
 * Class for cluster management
 */
public class ClusterManager {
	private final static Logger logger = LogManager.getLogger(ClusterManager.class);
	private final Map<PicoAddress, Node> cluster;
	private final Map<PicoAddress, Integer> suspectedMembers;
	private final Map<PicoContainer, Long> initTimes;
	private final PicoCommunication comm;
	private final NodeManager manager;
	private final ScheduledExecutorService scheduledPool = CommandLineArguments.scheduledPool;
	public final String CLUSTER_NAME = "k8-pico";
	private final ExecutorService pool;

	/**
	 * Constructor for the ClusterManager
	 * @param manager NodeManager object
	 */
	public ClusterManager(NodeManager manager) {
		this.cluster = new ConcurrentHashMap<>();
		this.initTimes = new ConcurrentHashMap<>();
		this.manager = manager;
		this.comm = new PicoCommunication(this, manager);
		this.suspectedMembers = new ConcurrentHashMap<PicoAddress, Integer>();
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

	/**
	 * Method to get the NodeManager
	 * @return NodeManager object
	 */
	public NodeManager getNodeManager() {
		return manager;
	}

	/**
	 * Method to get the PicoCommunication
	 * @return PicoCommunication object
	 */
	public PicoCommunication getCommunication() {
		return this.comm;
	}

	/**
	 * Method to get the leader of the cluster
	 * @return Address of the leader
	 */
	public PicoAddress getLeader() {
		List<PicoAddress> addresses = getClusterAddresses();
		Collections.sort(addresses);
		if (addresses.isEmpty())
			return manager.getAddress();
			
		return addresses.get(0);
	}

	/**
	 * Method to join a cluster
	 * @param address Address of the cluster
	 * @throws PicoException If the cluster cannot be joined
	 */
	public void joinCluster(PicoAddress address) throws PicoException {
		Node self = manager.getNode();

		// send join req and add members
		List<Node> newMembers = this.comm.joinRequest(address, self);

		for (Node node : newMembers)
			cluster.put(node.getAddress(), node);

		//finally add ourselves
		cluster.put(manager.getAddress(), manager.getNode());
	}

	/**
	 * Method to get the members of the cluster
	 * @return List of nodes
	 */
	public List<Node> getClusterMembers() {
		return new ArrayList<Node>(cluster.values());
	}

	/**
	 * Method to get the addresses of the nodes in the cluster
	 * @return List of addresses
	 */
	public List<PicoAddress> getClusterAddresses() {
		Set<PicoAddress> res = cluster.keySet();
		return new ArrayList<>(res);
	}

	/**
	 * Method to get the nodes in the cluster
	 * @return List of nodes
	 */
	public List<Node> getNodes() {
		return new ArrayList<Node>(cluster.values());
	}

	/**
	 * Method to add a node to the cluster
	 * @param node Node object
	 */
	public void addNode(Node node) {
		cluster.put(node.getAddress(), node);
		suspectedMembers.remove(node.getAddress());
		logger.info("Cluster now contains {} members", cluster.size());
	}

	/**
	 * Method to remove a node from the cluster
	 * @param node Node object
	 */
	public void removeNode(Node node) {
		cluster.remove(node.getAddress());
	}

	/**
	 * Method to remove a node from the cluster
	 * @param adr Address of the node
	 */
	public void removeNode(PicoAddress adr) {
		cluster.remove(adr);
	}

	/**
	 * Method to send a leave request to a remote node
	 * @param adr Address of the remote node
	 */
	public void leaveRemote(PicoAddress adr) {
		comm.leaveRemote(adr);
	}

	/**
	 * Method to update a node in the cluster
	 * @param node Node object
	 */
	public void updateNode(Node node) {
		cluster.put(node.getAddress(), node);
	}

	/**
	 * Method to get a node by its address
	 * @param address Address of the node
	 * @return Node object
	 */
	public Node getNode(PicoAddress address) {
		return cluster.get(address);
	}

	/**
	 * Method to get the current node
	 * @return Node object
	 */
	public Node fetchNode() {
		return this.manager.getNode();
	}

	/**
	 * Method to get a node by its address
	 * @param adr Address of the node
	 * @return Node object
	 */
	public Node fetchNode(PicoAddress adr) {
		return this.comm.fetchNode(adr);
	}

	/**
	 * Method to get the performance of the current node
	 * @return Performance object
	 */
	public Performance fetchNodePerformance() {
		return this.manager.getNodePerformance();
	}

	/**
	 * Method to get the performance of a remote node by its address
	 * @param adr Address of the node
	 * @return Performance object
	 */
	public Performance fetchNodePerformance(PicoAddress adr) {
		return this.comm.fetchNodePerformance(adr);
	}

	/**
	 * Heartbeat method to send heartbeats to all nodes in the cluster
	 * and handle suspected dead nodes
	 */
	public void heartbeat() {
		List<Node> members = getClusterMembers();

		pool.submit(() -> {
			long start = System.currentTimeMillis();

			for (Node node : members) {
				// Filter out self
				if (node.getAddress().equals(manager.getAddress()))
					continue;

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
			}
			long time = System.currentTimeMillis() - start;
			logger.info("Heartbeat with cluster of size {} completed after {} ms", cluster.size(), time);
		});
	}

	/**
	 * Method to check if a node is dead by sending a suspect request to all
	 * nodes. If all nodes flag the node as suspected, it is considered dead.
	 * @param adr Address of the node
	 * @return True if the node is dead, false otherwise
	 */
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

	/**
	 * Method to check if a node is suspected
	 * @param adr Address of the node
	 * @return True if the node is suspected, false otherwise
	 */
	public boolean isSuspect(PicoAddress adr) {
		return suspectedMembers.containsKey(adr);
	}

	/**
	 * Method to create a container by sending a container election request to
	 * the leader
	 * @param container PicoContainer object
	 */
	public void createContainer(PicoContainer container) {
		logger.info("Initializing container creation for {} ...", container.getName());
		initTimes.put(container, System.currentTimeMillis());
		PicoAddress leader = getLeader();
		this.comm.initiateContainerElection(container, leader);
	}

	/**
	 * Method to add a container to a remote node
	 * @param address Address of the remote node
	 * @param container PicoContainer object
	 */
	public void addContainerToNode(PicoAddress address, PicoContainer container) {
		Node n = cluster.get(address);
		if (n == null) {
			logger.warn("Cannot add container to node: No node with address {} in cluster", address);
			return;
		}
		n.addContainer(container);
		if (initTimes.containsKey(container)) {
			long createTime = System.currentTimeMillis() - initTimes.get(container);
			logger.info("Finished container election process for {} after {} ms", container.getName(), createTime);
		}
	}

	/**
	 * Method to get all the containers of a remote node
	 * @param adr Address of the remote node
	 * @return List of PicoContainer objects
	 */
	public List<PicoContainer> getContainers(PicoAddress adr) {
		Node n = this.cluster.get(adr);
		return n.getContainers();
	}

	/**
	 * Method to get all the containers in the cluster
	 * @return List of PicoContainer objects
	 */
	public List<PicoContainer> getAllContainers() {
		//ignore duplicates if running multiple instance on same node
		HashSet<PicoContainer> set = new HashSet<>(); 
	
		for (Node n : getNodes()) 
			set.addAll(n.getContainers());
		
		return new ArrayList<>(set);
	}

	/**
	 * Method to get a container by its name
	 * @param name Name of the container
	 * @return PicoContainer object
	 */
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