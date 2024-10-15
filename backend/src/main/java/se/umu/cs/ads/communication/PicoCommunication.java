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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.messagehandler.MessageHandler;
import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.serializers.ContainerSerializer;
import se.umu.cs.ads.serializers.NodeSerializer;
import se.umu.cs.ads.types.JMessage;
import se.umu.cs.ads.types.MessageType;
import se.umu.cs.ads.types.Node;
import se.umu.cs.ads.types.PicoAddress;
import se.umu.cs.ads.types.PicoContainer;

public class PicoCommunication {
	private static final Logger logger = LogManager.getLogger(PicoCommunication.class);
	private final PicoServer server;
	private final PicoAddress address;
	private final Set<JMessage> receivedMessages;
	private final ClusterManager cluster;
	private final ExecutorService pool;
	private final PicoClient client;
	private final MessageHandler handler;
	private final NodeManager manager;

	public PicoCommunication(ClusterManager cluster, NodeManager manager) {
		this.manager = manager;
		this.address = manager.getAddress();
		this.server = new PicoServer(this);
		this.client = new PicoClient(address);
		this.receivedMessages = ConcurrentHashMap.newKeySet();
		this.cluster = cluster;
		this.pool = CommandLineArguments.pool;
		this.handler = new MessageHandler(cluster.getNodeManager(), cluster);

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

	public List<JMessage> broadcast(JMessage msg) throws PicoException {
		List<PicoAddress> addresses = cluster.getClusterAddresses();
		// return broadcast(addresses, msg);
		return new ArrayList<>();
	}


	/**
	 * Adds a new member to the cluster
	 * @param address
	 */
	public void addNewMember(RpcNode node) {
		Node n = NodeSerializer.fromRPC(node);
		this.cluster.addNode(n);
	}

	public JMessage receive(JMessage message) {
		// Reliable multicast
		// Have we received this message before, ín that case do nothing
		MessageType type = message.getType();
		PicoAddress sender = message.getSender();
		logger.info("Received {} message from {}", type, sender);

		System.out.println(message.toString());

		if (receivedMessages.contains(message)) {
			logger.info("Message {} from {} has already been received", type, sender);
			return null; // TODO: return JMessage
		}

		receivedMessages.add(message);
		logger.info("{} message from {} has not been received before, broadcasting to other members...", type, sender);
		cluster.broadcast(message);

		// handle message here

		return handler.handle(message); // TODO: return JMessage
	}

	/**
	 * When another member wished to leave the cluster
	 * 
	 * @param message
	 * @return
	 */
	public JMessage leave(JMessage message) {
		Object payload = message.getPayload();
		if (!(payload instanceof Node) || payload == null) {
			String err = "Message type was LEAVE_REQUEST but payload not instance of Node";
			logger.error(err);
			return JMessage.ERROR(err);
		}

		Node node = (Node) message.getPayload();
		this.cluster.removeNode(node);
		List<JMessage> replies = broadcast(message);
		return new JMessage();
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
			} catch (InterruptedException | ExecutionException | CancellationException e) {
				exceptions.add(e);
				logger.warn("Exception while evaluating container {} from: {}", 
					container.getName(), e.getMessage());
				continue;
			}
			double score = eval.getScore();

			if (score == manager.NAME_CONFLICT) {
				nameConflict = true;
				break;
			}

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
}
