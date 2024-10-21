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

public class PicoCommunication {
	private static final Logger logger = LogManager.getLogger(PicoCommunication.class);
	private final PicoServer server;
	private final PicoAddress address;
	private final ClusterManager cluster;
	private final ExecutorService pool;
	private final PicoClient client;
	private final NodeManager manager;

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


	public List<PicoAddress> getClusterAddresses() {
		return this.cluster.getClusterAddresses();
	}


	public PicoAddress getAddress() {
		return address;
	}


	/**
	 * Adds a new member to the cluster
	 * @param address
	 */
	public void addNewMember(RpcNode node) {
		Node n = NodeSerializer.fromRPC(node);
		this.cluster.addNode(n);
	}

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

	public synchronized RpcNodes joinReply(RpcNode msg) {
		Node newMember = NodeSerializer.fromRPC(msg);
		cluster.addNode(newMember);
		List<Node> nodes = cluster.getNodes();

		return NodeSerializer.toRPC(nodes);
	}

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

	public RpcContainer createContainer(RpcContainer container) {
		
		PicoContainer result = this.manager.createLocalContainer(ContainerSerializer.fromRPC(container));
		return ContainerSerializer.toRPC(result);
	}

	public RpcContainer startContainer(RpcContainer container) throws PicoException {
		PicoContainer resutl = this.manager.startContainer(container.getName());
		return ContainerSerializer.toRPC(resutl);
	}
	
	public Node fetchNode() {
		return this.cluster.fetchNode();
	}

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

	public void removeNode(PicoAddress adr) {
		this.cluster.removeNode(adr);
		logger.info("Removed node {}", adr);
	}

	public void leaveRemote(PicoAddress adr) {
		try {
			logger.info("Sending LEAVE to {}", adr);
			this.client.leave(adr);
		} catch (Exception e) {
			logger.error("Failed to remove node: {}", e);
			throw new PicoException(e.getMessage());
		}
	}

	// removes node from address, removes self if toRemove is null
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

	public Node heartbeatRemote(PicoAddress remote) throws PicoException {
		try {
			return this.client.heartbeat(remote);
		} catch (Exception e) {
			throw new PicoException(e.getMessage());
		}
	}

	public boolean isSuspect(PicoAddress adr) {
		return this.cluster.isSuspect(adr);
	}

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

	public RpcContainerEvaluation evaluateContainer(RpcContainer container) {
		PicoContainer cont = ContainerSerializer.fromRPC(container);
		double evaluation = this.manager.evaluateContainer(cont);
		return RpcContainerEvaluation.newBuilder()
			.setContainer(container)
			.setSender(getSelfMetadata())
			.setScore(evaluation)
			.build();
	}



	public void initiateContainerElection(PicoContainer container, PicoAddress remote) {
		RpcContainer rpc = ContainerSerializer.toRPC(container);
		client.containerElectionStart(rpc, remote);
	}


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

	private RpcMetadata getSelfMetadata() {
		return RpcMetadata.newBuilder()
			.setIp(this.address.getIP())
			.setPort(this.address.getPort())
			.build();
	}

	public synchronized RpcContainer createLocalContainer(RpcContainer rpc) {
		PicoContainer container = ContainerSerializer.fromRPC(rpc);
		PicoContainer res = this.manager.createLocalContainer(container);
		return ContainerSerializer.toRPC(res);
	}

	public void receiveElectionEnd(RpcContainer rpc, RpcMetadata newHost) {
		PicoAddress address = new PicoAddress(newHost.getIp(), newHost.getPort());
		PicoContainer container = ContainerSerializer.fromRPC(rpc);
		cluster.addContainerToNode(address, container);
	}
	public void broadcastElectionEnd(RpcContainer rpc) {
		PicoAddress self = getAddress();
		List<PicoAddress> addresses = cluster.getClusterAddresses();

		for (PicoAddress remote : addresses) {
			pool.submit(() -> {
				client.markElectionEnd(rpc, self, remote);
			});
		}
	}

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

	public String sendCommunicationCommand(PicoContainer container, PicoAddress remote, ContainerCommand cmd) {
		RpcContainer rpc = ContainerSerializer.toRPC(container);
		RpcContainerCommand msg = RpcContainerCommand.newBuilder()
			.setContainer(rpc)
			.setCommand(cmd)
			.build();
		return client.sendContainerCommand(msg, remote);
	}
}
