package se.umu.cs.ads.nodemanager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.controller.Controller;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.metrics.SystemMetric;
import se.umu.cs.ads.types.JMessage;
import se.umu.cs.ads.types.Node;
import se.umu.cs.ads.types.Performance;
import se.umu.cs.ads.types.PicoAddress;
import se.umu.cs.ads.types.PicoContainer;
import se.umu.cs.ads.utils.Util;
/**
 * Class for cluster management
 */
public class NodeManager {
	private final static Logger logger = LogManager.getLogger(NodeManager.class);
	private final Controller controller;
	private final SystemMetric metrics;
	private final ClusterManager cluster;
	public final Node node;
	public final double NAME_CONFLICT = -1.0;
	public final double PORT_CONFLICT = -2.0;
	// name, containers
	private final Map<PicoAddress, List<PicoContainer>> remoteContainers;


	@SuppressWarnings("static-access")
	public NodeManager(Controller controller) {
		int port = CommandLineArguments.grpcPort;
		String ip = Util.getLocalIP();

		this.node = new Node();
		this.node.setAddress(new PicoAddress(ip, port));
		logger.info("Nodes address is set to {}:{}.", ip, port);

		this.cluster = new ClusterManager(this);
		this.node.setCluster(cluster.CLUSTER_NAME);

		this.controller = controller;
		this.remoteContainers = new ConcurrentHashMap<>();
		this.metrics = new SystemMetric();
	}

	public ClusterManager getClusterManager() {
		return this.cluster;
	}

	// Node information management -------------------------------------------------

	public Node getNode() {
		return this.node;
	}

	public PicoAddress getAddress() {
		return this.node.getAddress();
	}

	public Node getNode(PicoAddress ipPort) throws PicoException {
		if (getAddress().equals(ipPort)) {
			return this.node;
		}

		return this.cluster.fetchNode(ipPort);
	}

	public List<Node> getNodes() throws Exception {
		return this.cluster.getNodes();

		// JMessage msg = new JMessage(
		// MessageType.FETCH_NODES,
		// ""
		// );

		// return broadcast(msg).stream()
		// .map(obj -> (Node) obj.getPayload())
		// .toList();
	}

	public Performance getNodePerformance() {
		return new Performance(
			getCPULoad(),
			getMemLoad()
		);
	}

	public Performance getNodePerformance(PicoAddress ipPort) {
		if (getAddress().equals((ipPort))) {
			return new Performance(
				getCPULoad(),
				getMemLoad()
			);
		}

		return this.cluster.fetchNodePerformance(ipPort);
	}

	public synchronized void setActiveContainers(List<PicoContainer> containers) {
		this.node.setContainers(containers);
	}

	// Cluster and channel management ----------------------------------------------

	// public boolean isLeader() {
	// return getLeader().toString().equals(getChannelAddress());
	// }

	public PicoAddress getLeader() {
		List<PicoAddress> addresses = cluster.getClusterAddresses();
		Collections.sort(addresses);
		if (addresses.isEmpty())
			return null;
			
		return addresses.get(0);
	}

	/**
	 * Add a collection of containers to the list of known host for a remote
	 * 
	 * @param name       name of the remote host
	 * @param containers containers to add
	 */
	public void updateRemoteContainers(PicoAddress name, List<PicoContainer> containers) {
		List<PicoContainer> existing = remoteContainers.get(name);
		if (existing == null) {
			existing = new ArrayList<>();
		}

		existing.addAll(containers);
		remoteContainers.put(name, existing);
	}

	/**
	 * Add a container to the list of known remote containers for the given host
	 * 
	 * @param name      name of the host
	 * @param container container to add
	 */
	public void updateRemoteContainers(PicoAddress name, PicoContainer container) {
		List<PicoContainer> existingContainers = remoteContainers.get(name);
		if (existingContainers == null) {
			existingContainers = new ArrayList<>();

		}

		existingContainers.add(container);
		remoteContainers.put(name, existingContainers);
	}

	/**
	 * Returns a copy of the provided hosts containers
	 * 
	 * @param name the name of the host
	 * @return list of all container
	 */
	public List<PicoContainer> getRemoteContainers(String name) {
		// We create a copy so the original list can't be modified
		List<PicoContainer> existing = remoteContainers.get(name);
		List<PicoContainer> copy = new ArrayList<>();
		if (existing == null)
			existing = new ArrayList<>();

		copy.addAll(existing);
		return copy;
	}

	public double getCPULoad() {
		return metrics.getCPULoad();
	}

	public double getMemLoad() {
		return metrics.getMemoryLoad();
	}

	public double getFreeMem() {
		return metrics.getFreeMemory();
	}

	public boolean hasContainerName(String name) {
		List<PicoContainer> conts = node.getContainers();
		int hasName = (int) conts.stream().filter(it -> it.getName().equals(name)).count();
		return hasName > 0;
	}

	/**
	 * Check if any of the currently running containers have any of the provided ports
	 * @param ports
	 * @return
	 */
	public List<Integer> conflictingPorts(Set<Integer> external) {
		List<Integer> conflicting = new ArrayList<>();

		for (Integer port : external) {
			for (PicoContainer cont : node.getContainers()) {
				Set<Integer> currentExternal = cont.getPortsMap().keySet();
				//we have a conflict
				if (currentExternal.contains(port))
					conflicting.add(port);
			}
		}
		return conflicting;
	}

	public double evaluateContainer(PicoContainer container) {
		List<Integer> portConflicts = conflictingPorts(container.getPortsMap().keySet());
		boolean nameConflict = hasContainerName(container.getName());
		
		if (nameConflict) {
			logger.warn("Container {} under evaluation has conflicting a names!", container.getName());
			return NAME_CONFLICT;
		}

		else if (!portConflicts.isEmpty()) {
			logger.warn("Container {} has ports conflicts: {}", Arrays.toString(portConflicts.toArray()));
			return PORT_CONFLICT;
		}

		return getScore();
	}

	/**
	 * High score indicates high load
	 */
	public double getScore() {
		double w_cpu = 1;
		double w_mem = 1;

		double cpuFree = 1 - getCPULoad();
		double memFree = 1 - getMemLoad();

		// 0-2 with low load. 1-3 with meduim load. 2-4 with high load
		if (cpuFree < 0.2) {
			w_cpu = 1 + getCPULoad();
			w_cpu *= 2;
		}
		if (memFree < 0.2) {
			w_mem = 1 + getMemLoad();
			w_mem *= 2;
		}

		return (w_cpu * cpuFree) + (w_mem * memFree);
	}

	/**
	 * Broadcast a message over the cluster
	 * 
	 * @throws Exception exception
	 */
	public List<JMessage> broadcast(JMessage msg) throws Exception {
		// IP/Port should be added somewhere lower
		return this.cluster.broadcast(msg);
	}

	/**
	 * Send a message to a specific node
	 */
	public JMessage send(JMessage msg) {
		return this.cluster.send(msg);
	}

	public ExecutorService getPool() {
		return this.controller.getPool();
	}

	public PicoContainer createLocalContainer(PicoContainer container) {
		return this.controller.createLocalContainer(container);
	}

	public PicoContainer startContainer(String name) {
		return this.controller.startContainer(name);
	}

	public void receive(JMessage message) {
		// handle message here
	}

	public PicoAddress selectBestRemote(Map<PicoAddress, Double> evaluations) {
		double minScore = Double.MAX_VALUE;
		PicoAddress minRemote = null;

		for (PicoAddress remote : evaluations.keySet()) {
			double score = evaluations.get(remote);
			
			//if there is a name conflict the container is invalid
			if (score == NAME_CONFLICT) 
				return null;
			
			//if there is a port conflict it can still run, just not on this machine
			else if (score == PORT_CONFLICT)
				continue;

			else if (score < minScore) {
				minScore = score;
				minRemote = remote;
			}
		}

		if (minRemote != null) 
			logger.info("Selected remote {} with score {} as best suited", minRemote, minScore);		
		else 
			logger.warn("No remote met the criteria. The container cannot be created");
		
		return minRemote;
	} 

	public void removeNode(PicoAddress adr) {
		this.cluster.removeNode(adr);
	}
}