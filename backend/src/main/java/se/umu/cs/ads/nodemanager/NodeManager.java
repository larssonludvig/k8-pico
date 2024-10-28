package se.umu.cs.ads.nodemanager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.*;

import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.communication.ContainerCommand;
import se.umu.cs.ads.controller.Controller;
import se.umu.cs.ads.exception.*;
import se.umu.cs.ads.metrics.SystemMetric;
import se.umu.cs.ads.types.*;
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

	/**
	 * Constructor for the NodeManager
	 * @param controller Controller object
	 */
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
		this.metrics = new SystemMetric();
	}

	/**
	 * Method to get the cluster manager.
	 * @return ClusterManager object
	 */
	public ClusterManager getClusterManager() {
		return this.cluster;
	}

	/**
	 * Method to get the current node.
	 * @return Node object
	 */
	public Node getNode() {
		return this.node;
	}

	/**
	 * Method to get the current node's address.
	 * @return Address of the node
	 */
	public PicoAddress getAddress() {
		return this.node.getAddress();
	}

	/**
	 * Method to get a node by its address.
	 * @param ipPort Address of the node
	 * @return Node object
	 * @throws PicoException If the node is not found
	 */
	public Node getNode(PicoAddress ipPort) throws PicoException {
		if (getAddress().equals(ipPort)) {
			return this.node;
		}

		return this.cluster.fetchNode(ipPort);
	}

	/**
	 * Method to get all the nodes in the cluster.
	 * @return List of nodes
	 * @throws PicoException If something goes wrong
	 */
	public List<Node> getNodes() throws Exception {
		return this.cluster.getNodes();
	}

	/**
	 * Method to get the performance of the node.
	 * @return Performance object
	 */
	public Performance getNodePerformance() {
		return new Performance(
			getCPULoad(),
			getMemLoad()
		);
	}

	/**
	 * Method to get the performance of a node by its address.
	 * @param ipPort Address of the node
	 * @return Performance object
	 */
	public Performance getNodePerformance(PicoAddress ipPort) {
		if (getAddress().equals((ipPort))) {
			return new Performance(
				getCPULoad(),
				1 - getMemLoad()
			);
		}

		return this.cluster.fetchNodePerformance(ipPort);
	}

	/**
	 * Method to set the active containers of the node.
	 * @param containers List of containers
	 */
	public synchronized void setActiveContainers(List<PicoContainer> containers) {
		this.node.setContainers(containers);
	}

	/**
	 * Method to get the CPU load of the current node.
	 * @return CPU load
	 */
	public double getCPULoad() {
		return metrics.getCPULoad();
	}

	/**
	 * Method to get the memory load of the current node.
	 * @return Memory load
	 */
	public double getMemLoad() {
		return metrics.getMemoryLoad();
	}

	/**
	 * Method to get the free memory of the current node.
	 * @return Free memory
	 */
	public double getFreeMem() {
		return metrics.getFreeMemory();
	}

	/**
	 * Method that checks if the node has a container with a specific name.
	 * @param name Name of the container
	 * @return True if the container exists, false otherwise
	 */
	public boolean hasContainerName(String name) {
		List<PicoContainer> conts = node.getContainers();
		int hasName = (int) conts.stream().filter(it -> it.getName().equals(name)).count();
		return hasName > 0;
	}

	/**
	 * Check if any of the currently running containers have any of the provided ports
	 * @param external Set of ports to check
	 * @return List of conflicting ports
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

	/**
	 * Method to evaluate the load on the current node.
	 * @param container Container to evaluate
	 * @return Score of the container
	 */
	public double evaluateContainer(PicoContainer container) {
		List<Integer> portConflicts = conflictingPorts(container.getPortsMap().keySet());
		boolean nameConflict = hasContainerName(container.getName());
		
		if (nameConflict) {
			logger.warn("Container {} under evaluation has conflicting a names!", container.getName());
			throw new NameConflictException(container.getName());
		}

		else if (!portConflicts.isEmpty()) {
			logger.warn("Container {} has ports conflicts: {}", Arrays.toString(portConflicts.toArray()));
			int[] ports = portConflicts.stream().mapToInt(Integer::intValue).toArray();
			throw new PortConflictException(ports);
		}
		double score = getScore();
		logger.info("Evaluated container {} with score {}", container.getName(), score);
		return score;
	}

	/**
	 * Method to evaluate the load on the loacl node. A high score indicates
	 * a high load on the node.
	 * @return Score of the container
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
	 * Method to create a container on the local node.
	 * @param container Container to create
	 * @return Container object
	 */
	public PicoContainer createLocalContainer(PicoContainer container) {
		return this.controller.createLocalContainer(container);
	}

	/**
	 * Method to start a container on the local node.
	 * @param name Name of the container
	 * @return Container object
	 */
	public PicoContainer startContainer(String name) {
		return this.controller.startContainer(name);
	}

	/**
	 * Method to restart a container on the local node.
	 * @param name Name of the container
	 */
	public void restartContainer(String name) {
		this.controller.restartContainer(name);
	}

	/**
	 * Method to stop a container on the local node.
	 * @param name Name of the container
	 */
	public void stopContainer(String name) {
		this.controller.stopContainer(name);
	}

	/**
	 * Method to remove a container on the local node.
	 * @param name Name of the container
	 */
	public void removeContainer(String name) {
		this.controller.removeContainer(name);
	}

	/**
	 * Method to send a command to a container on a remote node.
	 * @param name Name of the container
	 * @param command Command to send
	 * @return Response from the container
	 */
	public String remoteContainerCommand(String name, String command) {
		//find container and the node that has it
		PicoAddress remote = null;
		PicoContainer container = null;
		ArrayList<Node> nodes = new ArrayList<>(cluster.getClusterMembers());
		for (Node n : nodes) {
			for (PicoContainer cont : n.getContainers()) {
				if (cont.getName().equals(name)) {
					remote = n.getAddress();
					container = cont;
				}
			}
		}

		if (remote == null || container == null) {
			logger.warn("Could not find any container with name {}", name);
			return null;
		}
		ContainerCommand cmd = parseCommand(command);
		if (cmd == null) {
			logger.warn("Could not parse command {}, ignoring", command);
			return "";
		}

		return cluster.getCommunication().sendCommunicationCommand(container, remote, cmd);
	}

	/**
	 * Method to parse a command to a container.
	 * @param name Name of the container
	 * @param command Command to parse
	 * @return Parsed command
	 */
	private ContainerCommand parseCommand(String command) {
		switch (command) {
			case "START":
				return ContainerCommand.START;
			case "RESTART":
				return ContainerCommand.RESTART;
			case "STOP":
				return ContainerCommand.STOP;
			case "REMOVE":
				return ContainerCommand.REMOVE;
			case "FETCH_LOGS":
				return ContainerCommand.GET_LOGS;
			default:
				return null;
		}
	}

	/**
	 * Method to get the logs of a container on the local node.
	 * @param name Name of the container
	 * @return Logs of the container
	 */
	public String getContainerLogs(String name) throws PicoException {
		List<String> logs = this.controller.getContainerLogs(name);
		StringBuilder builder = new StringBuilder();
		for (String log : logs) {
			builder.append(log).append("\n");
		}
		return builder.toString();
	}

	/**
	 * Method to evaluate the node with the best score.
	 * @param evaluations Map of evaluations
	 * @return Address of the best node
	 */
	public PicoAddress selectBestRemote(Map<PicoAddress, Double> evaluations) {
		double minScore = Double.MAX_VALUE;
		PicoAddress minRemote = null;

		for (PicoAddress remote : evaluations.keySet()) {
			double score = evaluations.get(remote);
			if (score < minScore) {
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

	/**
	 * Method to remove a node from the cluster.
	 * @param adr Address of the node
	 */
	public void removeNode(PicoAddress adr) {
		this.cluster.removeNode(adr);
	}
}