package se.umu.cs.ads.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.messagehandler.MessageHandler;
import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.serializers.ContainerSerializer;
import se.umu.cs.ads.serializers.NodeSerializer;
import se.umu.cs.ads.types.*;

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
		this.pool = Executors.newCachedThreadPool();
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

	public List<Node> joinRequest(PicoAddress remote, Node self) throws PicoException {

		RpcContainers.Builder builder = RpcContainers.newBuilder();
		self.getContainers().forEach(it -> ContainerSerializer.toRPC(it));

		RpcNode rpcSelf = RpcNode.newBuilder()
				.setClusterName(self.getCluster())
				.setIp(self.getIP())
				.setPort(self.getPort())
				.setContainers(builder.build())
				.build();

		RpcMetadata sender = RpcMetadata.newBuilder().setIp(self.getIP()).setPort(self.getPort()).build();
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

	public Node heartBeat(PicoAddress adr) {
		return this.cluster.heartBeat(adr);
	}
}
