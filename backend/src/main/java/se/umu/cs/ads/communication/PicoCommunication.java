package se.umu.cs.ads.communication;

import java.util.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.*;

import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.exception.*;
import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.serializers.*;
import se.umu.cs.ads.types.*;

/**
 * Class for communication between nodes
 */
public class PicoCommunication {
	private static final Logger logger = LogManager.getLogger(PicoCommunication.class);
	private final PicoServer server;
	private final PicoAddress address;
	private final ClusterManager cluster;
	private final ExecutorService pool;
	private final PicoClient client;
	private final NodeManager manager;

	/**
	 * Constructor for the PicoCommunication
	 * @param cluster ClusterManager object
	 * @param manager NodeManager object
	 */
	public PicoCommunication(ClusterManager cluster, NodeManager manager) {
		this.manager = manager;
		this.address = manager.getAddress();
		this.server = new PicoServer(this);
		this.client = new PicoClient();
		this.cluster = cluster;
		this.pool = CommandLineArguments.pool;

		try {
			this.server.start();
		} catch (Exception e) {
			logger.error("Failed to rpc start server", e);
		}
	}

	/**
	 * Method for getting the addresses of nodes in the cluster.
	 * @return List of PicoAddress objects
	 */
	public List<PicoAddress> getClusterAddresses() {
		return this.cluster.getClusterAddresses();
	}

	/**
	 * Method for getting the address of the current node.
	 * @return PicoAddress object
	 */
	public PicoAddress getAddress() {
		return address;
	}


	/**
	 * Adds a new member to the cluster
	 * @param node the new member to add
	 */
	public void addNewMember(RpcNode node) {
		Node n = NodeSerializer.fromRPC(node);
		this.cluster.addNode(n);
	}

	/**
	 * Method for sending a join request to a remote node.
	 * @param remote Address of the remote node
	 * @param aspirant Node object
	 * @return List of Node objects
	 * @throws PicoException If the join request fails
	 */
	public synchronized List<Node> joinRequest(PicoAddress remote, Node aspirant) throws PicoException {
		RpcContainers.Builder builder = RpcContainers.newBuilder();
		// aspirant.getContainers().forEach(it -> ContainerSerializer.toRPC(it));

		RpcNode rpcAspirant = RpcNode.newBuilder()
				.setClusterName(aspirant.getCluster())
				.setIp(aspirant.getIP())
				.setPort(aspirant.getPort())
				.setContainers(builder.build())
				.build();

		RpcMetadata sender = getSelfMetadata();
		RpcJoinRequest request = RpcJoinRequest.newBuilder().setAspirant(rpcAspirant).setSender(sender).build();
		try {
			RpcNodes nodes = client.join(remote, request);
			return NodeSerializer.fromRPC(nodes);
		} catch (Exception e) {
			logger.error("Failed to join cluster: {}", e.getMessage());
			throw new PicoException(e.getMessage());
		}

	}

	/**
	 * Method for constructing a reply to a join request.
	 * @param msg Join request message
	 * @return List of Node objects
	 */
	public synchronized RpcNodes joinReply(RpcNode msg) {
		Node newMember = NodeSerializer.fromRPC(msg);
		cluster.addNode(newMember);
		List<Node> nodes = cluster.getNodes();

		return NodeSerializer.toRPC(nodes);
	}

	/**
	 * Method for fetching the performance of a node.
	 * @return RpcPerformance rpc object
	 */
	public RpcPerformance fetchPerformance() {
		double cpuLoad = this.manager.getCPULoad();
		double ramLoad = this.manager.getMemLoad();
		double ramAvailable = this.manager.getFreeMem();

		return RpcPerformance.newBuilder()
			.setCpuLoad(cpuLoad)
			.setMemLoad(ramLoad)
			.setFreeRam(ramAvailable)
			.build();
	}

	/**
	 * Method for creating a local container.
	 * @param container Container object
	 * @return RpcContainer object
	 */
	public RpcContainer createContainer(RpcContainer container) {
		
		PicoContainer result = this.manager.createLocalContainer(ContainerSerializer.fromRPC(container));
		return ContainerSerializer.toRPC(result);
	}

	/**
	 * Method for starting a local container.
	 * @param container Container object
	 * @return RpcContainer object
	 * @throws PicoException If the container cannot be started
	 */
	public RpcContainer startContainer(RpcContainer container) throws PicoException {
		PicoContainer resutl = this.manager.startContainer(container.getName());
		return ContainerSerializer.toRPC(resutl);
	}
	
	/**
	 * Method for fetching a node from the cluster.
	 * @return Node object
	 */
	public Node fetchNode() {
		return this.cluster.fetchNode();
	}

	/**
	 * Method for fetching a node from the cluster by address.
	 * @param adr Address of the node
	 * @return Node object
	 */
	public Node fetchNode(PicoAddress adr) {
		if (!adr.equals(this.address)) {
			// Send request to correct node
			try {
				return this.client.fetchNode(adr);
			} catch (Exception e) {
				logger.error("Failed to fetch node: {}", e);
				throw new PicoException(e.getMessage());
			}
		}

		return this.cluster.fetchNode();
	}

	/**
	 * Method for fetching the performance of a node.
	 * @param adr Address of the node
	 * @return Performance object
	 */
	public Performance fetchNodePerformance(PicoAddress adr) {
		if (!adr.equals(this.address)) {
			try {
				RpcPerformance perf = this.client.fetchPerformance(adr);
				
				return new Performance(
					perf.getCpuLoad(),
					perf.getMemLoad()
				);
			} catch (Exception e) {
				logger.error("Failed to fetch node performance: {}", e);
				throw new PicoException(e.getMessage());
			}
		}

		return this.cluster.fetchNodePerformance();
	}

	/**
	 * Method for removing a node from the cluster.
	 * @param adr Address of the node
	 */
	public void removeNode(PicoAddress adr) {
		this.cluster.removeNode(adr);
		logger.info("Removed node {}", adr);
	}

	/**
	 * Method for sending a leave request to a remote node.
	 * @param adr Address of the remote node
	 */
	public void leaveRemote(PicoAddress adr) {
		try {
			logger.info("Sending LEAVE to {}", adr);
			this.client.leave(adr);
		} catch (Exception e) {
			logger.error("Failed to remove node: {}", e);
			throw new PicoException(e.getMessage());
		}
	}

	/**
	 * Mehod for removing a remote node from the cluster
	 * @param toRemove Address of the node to remove
	 */
	public void removeNodeRemote(PicoAddress toRemove) {
		List<Node> members = this.cluster.getNodes();
		boolean removeSelf = false;

		if (toRemove == null) {
			removeSelf = true;
			toRemove = this.manager.getAddress();
		}
		
		List<PicoContainer> zomboidContainers = this.cluster.getContainers(toRemove);

		manager.removeNode(toRemove);
		logger.info("Removed node {} from self", toRemove);

		for (Node member : members) {
			PicoAddress remote = member.getAddress();
			
			// Safeguard
			if (remote.equals(this.address))
				continue;

			if (remote.equals(this.cluster.getLeader()))
				continue;
				
			if (toRemove.equals(remote))
				continue;

			try {
				this.client.removeNode(remote, toRemove);
			} catch (Exception e) {
				removeSelf = true;
				logger.error("Failed to remove {} from remote", toRemove);
			}
		}
		
		// Send start container request of all node containers to 
		// leader node. This is to ensure that the containers are not lost
		logger.info("Leader: {}", this.cluster.getLeader());
		PicoAddress leader = this.cluster.getLeader();
		logger.info("MYSELF: {}", this.address);
		if (leader.equals(this.address)) {
			logger.info("Starting process to move running containers from {}", toRemove);
			for (PicoContainer cont : zomboidContainers) {
				if (cont.getState() == PicoContainerState.RUNNING) {
					try {
						containerElectionStart(ContainerSerializer.toRPC(cont));
					} catch (Exception e) {
						logger.error("Failed to move container {}", e);
					}
				}
			}
			logger.info("Finnished removing and moving {}", toRemove);
		}

		// Successfull exit if it removes self
		if (removeSelf)
			System.exit(0);

	}

	/**
	 * Method for sending a heartbeat to a remote node.
	 * @param remote Address of the remote node
	 * @return Node object
	 * @throws PicoException If the heartbeat fails
	 */
	public Node heartbeatRemote(PicoAddress remote) throws PicoException {
		try {
			return this.client.heartbeat(remote);
		} catch (Exception e) {
			throw new PicoException(e.getMessage());
		}
	}

	/**
	 * Method for checking if the given address is suspected by the current node
	 * @param adr Address of the node
	 * @return True if the node is suspected, false otherwise
	 */
	public boolean isSuspect(PicoAddress adr) {
		return this.cluster.isSuspect(adr);
	}

	/**
	 * Method for sending a isSuspect request to a remote node.
	 * @param remote Address of the remote node
	 * @param suspect Address of the suspected node
	 * @return True if the node is suspected, false otherwise
	 */
	public boolean isSuspectRemote(PicoAddress remote, PicoAddress suspect) throws Exception {
		return this.client.isSuspect(remote, suspect);
	}

	/**
	 * Evaluates a container at the remote host
	 * @param container container to evaluate
	 * @param remote remote to evaluate
	 * @return the score or what error ocurred
	 * @throws PicoException if something in the communication protocol errored.
	 */
	public double evaluateRemoteContainer(PicoContainer container, PicoAddress remote) throws PicoException {
		RpcContainer rpc = ContainerSerializer.toRPC(container);
		RpcContainerEvaluation reply = client.evaluateContainer(rpc, remote);
		return reply.getScore();
	}

	/**
	 * Evaluate the posibility for the container to run on the current node
	 * @param container container to evaluate
	 * @return the score of the container
	 */
	public RpcContainerEvaluation evaluateContainer(RpcContainer container) {
		PicoContainer cont = ContainerSerializer.fromRPC(container);
		double evaluation = this.manager.evaluateContainer(cont);
		return RpcContainerEvaluation.newBuilder()
			.setContainer(container)
			.setSender(getSelfMetadata())
			.setScore(evaluation)
			.build();
	}

	/** 
	 * Initiates a container election for a node on the cluster
	 * @param container container to evaluate
	 * @param remote remote to evaluate
	 */
	public void initiateContainerElection(PicoContainer container, PicoAddress remote) {
		RpcContainer rpc = ContainerSerializer.toRPC(container);
		client.containerElectionStart(rpc, remote);
	}

	/**
	 * Method for starting a container election
	 * @param container Container object
	 * @throws PicoException If the container cannot be started
	 */
	public void containerElectionStart(RpcContainer container) throws PicoException {
		//send container create to that node
		//that node sends container_election_end

		List<PicoAddress> clusterMembers = cluster.getClusterAddresses();
		ArrayList<Future<RpcContainerEvaluation>> responses = new ArrayList<>();
		logger.info("Starting container election for {}, sending evaluation request to {} nodes", 
			container.getName(), clusterMembers.size());

		//evaluate container at all hosts
		for (PicoAddress remote : clusterMembers) {
			Future<RpcContainerEvaluation> future = pool.submit(() -> {
				return client.evaluateContainer(container, remote);
			});
			responses.add(future);
		}

		HashMap<PicoAddress, Double> evaluations = new HashMap<>();
		ArrayList<Exception> exceptions = new ArrayList<>();
		boolean nameConflict = false;
		for (Future<RpcContainerEvaluation> future : responses) {

			RpcContainerEvaluation eval = null;
			try {
				eval = future.get();
			} catch (InterruptedException | CancellationException e) {
				exceptions.add(e);
				logger.warn("Exception while evaluating container {} from: {}", 
					container.getName(), e.getMessage());
				continue;
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (cause instanceof PortConflictException) {
					PortConflictException pce = (PortConflictException) cause;
					logger.warn("Container {} has port conflicts: {}", container.getName(), pce.getPorts());			
					continue;
				}
				
				else if (cause instanceof NameConflictException) 
					throw (NameConflictException) cause;
				

				else if (cause instanceof PicoException) 
					throw (PicoException) cause;

				else
					continue;
			}

			double score = eval.getScore();
			logger.info("Got score for {} from {}", score, eval.getSender());

			RpcMetadata sender = eval.getSender();
			PicoAddress remote = new PicoAddress(sender.getIp(), sender.getPort());
			evaluations.put(remote, score);
		}

		if (nameConflict) {
			//TODO: send error that node cannot be spawned
		}

		PicoAddress best = manager.selectBestRemote(evaluations);
		if (best == null) 
			throw new PicoException("Cannot run container on any host!");
		

		//send create-container to best
		try {

			if (best.equals(manager.getAddress()))
				createLocalContainer(container);
			else
				client.createContainer(container, best);
		} catch(Exception e) {
			logger.error("Could not send CREATE_CONTAINER to remote {}", best);
			throw new PicoException("Could not send CREATE_CONTAINER to remote " + best);
		}
	}

	/**
	 * Creates a metadata object of the current node
	 * @return metadata object
	 */
	private RpcMetadata getSelfMetadata() {
		return RpcMetadata.newBuilder()
			.setIp(this.address.getIP())
			.setPort(this.address.getPort())
			.build();
	}

	/**
	 * Creating a container on the local node
	 * @param rpc Container object
	 * @return RpcContainer object
	 */
	public synchronized RpcContainer createLocalContainer(RpcContainer rpc) {
		PicoContainer container = ContainerSerializer.fromRPC(rpc);
		PicoContainer res = this.manager.createLocalContainer(container);
		return ContainerSerializer.toRPC(res);
	}

	/**
	 * Handles a container election end sent from a remote node
	 * @param rpc Container object
	 * @param newHost Address of the new host
	 */
	public void receiveElectionEnd(RpcContainer rpc, RpcMetadata newHost) {
		PicoAddress address = new PicoAddress(newHost.getIp(), newHost.getPort());
		PicoContainer container = ContainerSerializer.fromRPC(rpc);
		cluster.addContainerToNode(address, container);
	}

	/**
	 * Broadcasts the end of a container election to all nodes in the cluster
	 * @param rpc Container object
	 */
	public void broadcastElectionEnd(RpcContainer rpc) {
		PicoAddress self = getAddress();
		List<PicoAddress> addresses = cluster.getClusterAddresses();

		for (PicoAddress remote : addresses) {
			pool.submit(() -> {
				client.markElectionEnd(rpc, self, remote);
			});
		}
	}

	/**
	 * Parses and handles a container command request
	 * @param command Container command object
	 * @return Response from the container
	 * @throws PicoException If the command fails
	 */
	public String handleContainerCommand(RpcContainerCommand command) throws PicoException {
		ContainerCommand cmd = command.getCommand();
		RpcContainer container = command.getContainer();
		String name = container.getName();
		String response = "OK";
		logger.info("Received command {} for container {}", cmd.toString(), container.getName());
		switch (cmd.getNumber()) {
			case ContainerCommand.START_VALUE:
				manager.startContainer(name);
				break;
			case ContainerCommand.RESTART_VALUE:
				manager.restartContainer(name);
				break;
			case ContainerCommand.STOP_VALUE:
				manager.stopContainer(name);
				break;
			case ContainerCommand.REMOVE_VALUE:
				manager.removeContainer(name);
				break;
			case ContainerCommand.GET_LOGS_VALUE:
				response = manager.getContainerLogs(name);
				break;
			default:
		}
		return response;
	}

	/**
	 * Sends a container command to a remote node
	 * @param container Container object
	 * @param remote Address of the remote node
	 * @param cmd Command to send
	 * @return Response from the container
	 */
	public String sendCommunicationCommand(PicoContainer container, PicoAddress remote, ContainerCommand cmd) {
		RpcContainer rpc = ContainerSerializer.toRPC(container);
		RpcContainerCommand msg = RpcContainerCommand.newBuilder()
			.setContainer(rpc)
			.setCommand(cmd)
			.build();
		return client.sendContainerCommand(msg, remote);
	}
}
