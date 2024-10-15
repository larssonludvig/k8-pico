package se.umu.cs.ads.communication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.naming.InterruptedNamingException;
import javax.sound.sampled.Port;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.exception.NameConflictException;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.exception.PortConflictException;
import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.serializers.ContainerSerializer;
import se.umu.cs.ads.serializers.NodeSerializer;
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

	public List<Node> joinRequest(PicoAddress remote, Node self) throws PicoException {

		RpcContainers.Builder builder = RpcContainers.newBuilder();
		self.getContainers().forEach(it -> ContainerSerializer.toRPC(it));

		RpcNode rpcSelf = RpcNode.newBuilder()
				.setClusterName(self.getCluster())
				.setIp(self.getIP())
				.setPort(self.getPort())
				.setContainers(builder.build())
				.build();

		RpcMetadata sender = getSelfMetadata();
		RpcJoinRequest request = RpcJoinRequest.newBuilder().setAspirant(rpcSelf).setSender(sender).build();
		try {
			RpcNodes nodes = client.join(remote, request);
			return NodeSerializer.fromRPC(nodes);
		} catch (Exception e) {
			logger.error("Failed to join cluster: {}", e);
			throw new PicoException(e.getMessage());
		}

	}

	public RpcNodes joinReply(RpcNode msg) {
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

	public void removeNodeRemote() {
		logger.error("Removing self from remote nodes...");
		List<Node> members = this.cluster.getNodes();
		PicoAddress selfAdr = this.manager.getAddress();

		try {
			for (Node member : members) {
				PicoAddress remote = member.getAddress();
				this.client.removeNode(remote, selfAdr);
			}
			
			// TODO: Send start container request of all node containers to 
			// leader node. This is to ensure that the containers are not lost

		} catch (Exception e) {
			logger.error("Failed to remove self ({}) from remote", selfAdr);
			System.exit(1);
		}

		// Successfull exit
		System.exit(0);
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
		RpcContainerEvaluation reply = null;
		try {
			reply = client.evaluateContainer(rpc, remote);
		} catch(Exception e) {
			String err = String.format("An error occurred while evaluating container for %s: %s", remote, e.getMessage());
			logger.error(err);
			throw new PicoException(err);
		}
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

	public RpcContainer createLocalContainer(RpcContainer rpc) {
		PicoContainer container = ContainerSerializer.fromRPC(rpc);
		PicoContainer res = this.manager.createLocalContainer(container);
		return ContainerSerializer.toRPC(res);
		
	}
}
