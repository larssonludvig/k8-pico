package se.umu.cs.ads.communication;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.*;
import java.util.concurrent.*;

import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.communication.RpcServiceGrpc.*;
import se.umu.cs.ads.exception.NameConflictException;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.exception.PortConflictException;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.serializers.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * gRPC client for communication with other nodes
 */
public class PicoClient {
	private final static Logger logger = LogManager.getLogger(PicoClient.class);
	private final Map<PicoAddress, ManagedChannel> channels;
	private final Map<PicoAddress, RpcServiceBlockingStub> stubs;
	private final static Executor pool = CommandLineArguments.pool;
   
	/**
	 * Constructor for the PicoClient
	 */
    public PicoClient() {
		this.stubs = new ConcurrentHashMap<>();
		this.channels = new ConcurrentHashMap<>();
    }

	/**
	 * Method to connect to a new host
	 * @param address PicoAddress object
	 */
	public void connectNewHost(PicoAddress address) {
		ManagedChannel channel = ManagedChannelBuilder
			.forAddress(address.getIP(), address.getPort())
			.usePlaintext()
			// .keepAliveTimeout(60, TimeUnit.SECONDS)
			// .keepAliveTime(60, TimeUnit.SECONDS)
			// .enableRetry()
			.build();

		RpcServiceBlockingStub stub = RpcServiceGrpc
			.newBlockingStub(channel)
			.withDeadlineAfter(5, TimeUnit.MINUTES);

		channels.put(address, channel);
		stubs.put(address, stub);
		logger.info("Connected to new host: {}!", address);
	}	

	/**
	 * Method to send a JOIN_REQUEST to a remote node
	 * @param remote PicoAddress object
	 * @param msg RpcJoinRequest object
	 * @return RpcNodes object
	 * @throws PicoException if an error occurs during the call
	 */
	public RpcNodes join(PicoAddress remote, RpcJoinRequest msg) throws PicoException {
		RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);
		
		logger.info("Sending JOIN_REQUEST to {} ...", remote);
		long start = System.currentTimeMillis();
		RpcNodes reply = null;
		try {
			reply = stub.join(msg);
		} catch (Exception e) {
			String err = String.format("Received error from %s when sending JOIN_REQUEST: %s", remote, e.getMessage());
			throw handleError(remote, err);
		}
		long end = System.currentTimeMillis();
		logger.info("Received reply for JOIN_REQUEST after {} ms", end - start);
		return reply;
	}

	/**
	 * Request to remove a node from the network
	 * @param remote PicoAddress object
	 * @throws Exception if an error occurs during the call
	 */
	public void leave(PicoAddress remote) throws Exception {
		RpcServiceBlockingStub stub = stubs.get(remote);
		
		if (stub == null) {
			connectNewHost(remote);
			stub = stubs.get(remote);
		}

		RpcMetadata meta = RpcMetadata.newBuilder()
			.setIp(remote.getIP())
			.setPort(remote.getPort())
			.build();

		logger.info("Sending LEAVE to {} ...", remote);
		long start = System.currentTimeMillis();
		try {
			stub.leave(meta);
			stubs.remove(remote);
		} catch (Exception e) {
			String err = String.format("Received error from %s when sending LEAVE: %s", remote, e.getMessage());
			handleError(remote, err);
		}
		long time = System.currentTimeMillis() - start;
		logger.info("Received LEAVE_REPLY from {} after {} ms", remote, time);
	}

	/**
	 * Request to remove self from remote node
	 * @param remote PicoAddress object
	 * @param self PicoAddress object
	 * @throws PicoException if an error occurs during the call
	 */
	public void removeNode(PicoAddress remote, PicoAddress self) throws PicoException {
		RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);

		RpcMetadata meta = RpcMetadata.newBuilder()
			.setIp(self.getIP())
			.setPort(self.getPort())
			.build();

		logger.info("Sending request to remove node {} to {}...", self, remote);
		long start = System.currentTimeMillis();
		try {
			stub.removeNode(meta);
			long time = System.currentTimeMillis() - start;
			logger.info("Received REMOVE_NODE reply from {} after {} ms", remote, time);
		} catch (Exception e) {
			String err = String.format("Received error when removing node %s: %s", remote, e.getMessage());
			throw handleError(remote, err);
		}
	}

	/**
	 * Request to fetch the performance of a remote node
	 * @param remote PicoAddress object
	 * @return RpcPerformance object
	 * @throws PicoException if an error occurs during the call
	 */
	public RpcPerformance fetchPerformance(PicoAddress remote) throws PicoException {
		RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);
		logger.info("Fetching performance from {}...", remote);		
		try {
			long start = System.currentTimeMillis();
			RpcPerformance result = stub.fetchNodePerformance(RpcEmpty.newBuilder().build());
			long time = System.currentTimeMillis() - start;
			logger.info("Done fetching performance from {} after {} ms", remote, time);
			return result;
		} catch (Exception e) {
			String err = String.format("Could not fetch performance from %s: %s", remote, e.getMessage());
			throw handleError(remote, err);
		}
	}

	/**
	 * Creates a new stub connection if it does not exist
	 * @param remote PicoAddress object
	 * @return RpcServiceBlockingStub object
	 */
	private RpcServiceBlockingStub addRemoteIfNotConnected(PicoAddress remote) {
		RpcServiceBlockingStub stub = stubs.get(remote);
		if (stub == null) {
			connectNewHost(remote);
			stub = stubs.get(remote);
		}
		return stub; 
	}

	/**
	 * Request to fetch the node information of a remote node
	 * @param remote PicoAddress object
	 * @return Node object
	 * @throws PicoException if an error occurs during the call
	 */
	public Node fetchNode(PicoAddress remote) throws PicoException {
        RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);

        RpcMetadata meta = RpcMetadata.newBuilder()
            .setIp(remote.getIP())
            .setPort(remote.getPort())
            .build();

        logger.info("Sending FETCH_NODE to {}...", remote);
		long start = System.currentTimeMillis();
        try {
			RpcNode reply = stub.fetchNode(meta);
			long time = System.currentTimeMillis() - start;
        	logger.info("Received reply from FETCH_NODE after {} ms", time);
        	return NodeSerializer.fromRPC(reply);
		} catch (Exception e) {
			String err = String.format("Received error from remote %s from FETCH_NODE: %s", remote, e.getMessage());
			throw handleError(remote, err);
		}
    }

	/**
	 * Request to evaluate the nodes in the network for a container
	 * @param container RpcContainer object
	 * @param remote PicoAddress object
	 * @return RpcContainerEvaluation object
	 * @throws PicoException if an error occurs during the call
	 */
	public RpcContainerEvaluation evaluateContainer(RpcContainer container, PicoAddress remote) throws PicoException {
		RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);
		logger.info("Sending evaluation request for {} to {} ...", container.getName(), remote);
		long start = System.currentTimeMillis();
		try {
			RpcContainerEvaluation res = stub.elvaluateContainer(container);
			long time = System.currentTimeMillis() - start;
			logger.info("Received evaluation reply ({}) from {} after {} ms", res.getScore(), remote, time);
			return res;
		} catch(Exception e) {
			String msg = e.getMessage();
			ManagedChannel channel = channels.get(remote);
			channel.shutdownNow();
			channels.remove(remote);
			stubs.remove(remote);

			String err = String.format("Received error from remote %s when evaluating container %s: %s",
				remote, container.getName(), e.getMessage());
			logger.error(err);

			if (msg.startsWith("NAME_CONFLICT"))
				throw new NameConflictException(msg);
			if (msg.startsWith("PORT_CONFLICT"))
				throw new PortConflictException(msg);
				
			throw new PicoException(err);
		}		
	}

	/**
	 * Request to start an election for a container
	 * @param container RpcContainer object
	 * @param remote PicoAddress object
	 * @throws PicoException if an error occurs during the call
	 */
	public void containerElectionStart(RpcContainer container, PicoAddress remote) throws PicoException {
		logger.info("Initiating CONTAINER_ELECTION_START for {} to {}", container.getName(), remote);
		RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);
		try {
			stub.containerElectionStart(container);
		} catch (Exception e) {
			String err = String.format("Received exception from CONTAINER_ELECTION_START: %s", e.getMessage());
			throw handleError(remote, err);
		}
	}

	/**
	 * Request to create a container on a remote node
	 * @param container RpcContainer object
	 * @param remote PicoAddress object
	 * @throws PicoException if an error occurs during the call
	 */
	public void createContainer(RpcContainer container, PicoAddress remote) throws PicoException {
		RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);
		logger.info("Sending CREATE_CONTAINER for container {} to {} ...", 
			container.getName(), remote);
		try {
			stub.createContainer(container);
		} catch (Exception e) {
			String err = String.format("Received error from remote %s when creating container %s: %s", 
				remote, container.getName(), e.getMessage());
			throw handleError(remote, err);
		}
	}

	/**
	 * Request to mark the election as ended on a remote node
	 * @param container RpcContainer object
	 * @param self PicoAddress object
	 * @param to PicoAddress object
	 * @throws PicoException if an error occurs during the call
	 */
	public void markElectionEnd(RpcContainer container, PicoAddress self, PicoAddress to) throws PicoException {
		RpcMetadata rpcSelf = RpcMetadata.newBuilder().setIp(self.getIP()).setPort(self.getPort()).build();
		RpcContainerElectionEnd msg = RpcContainerElectionEnd.newBuilder()
			.setContainer(container)
			.setSender(rpcSelf)
			.build();

		RpcServiceBlockingStub stub = addRemoteIfNotConnected(to);
		try {
			logger.info("Sending ELECTION_END for container {} to {} ...", container.getName(), to);
			stub.containerElectionEnd(msg);
		} catch (Exception e) {
			throw handleError(to, 
				String.format("Failed to send ELECTION_END for container %s to %s: %s",
					container.getName(), to, e.getMessage()));
		
		}
	}

	/**
	 * Request to send a command to a container on a remote node
	 * @param command RpcContainerCommand object
	 * @param remote PicoAddress object
	 * @return String response from the container
	 * @throws PicoException if an error occurs during the call
	 */
	public String sendContainerCommand(RpcContainerCommand command, PicoAddress remote) {
		RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);
		try {

			logger.info("Sending {} command to {} for container {}", 
				command.getCommand().toString(), remote, command.getContainer().getName());
			return stub.containerCommand(command).getPayload();
		} catch (Exception e) {
			throw handleError(remote, e.getMessage());
		}
	}

	/**
	 * Request to send a HEARTBEAT to a remote node
	 * @param remote PicoAddress object
	 * @return Node object
	 * @throws PicoException if an error occurs during the call
	 */
	public Node heartbeat(PicoAddress remote) throws Exception {
		RpcServiceBlockingStub stub = addRemoteIfNotConnected(remote);

		try {
			long start = System.currentTimeMillis();
			Node node = NodeSerializer.fromRPC(stub.heartbeat(RpcEmpty.newBuilder().build()));
			long time = System.currentTimeMillis() - start;
			logger.info("Successfully sent HEARTBEAT to {} after {} ms", remote, time);
			return node;
		} catch (Exception e) {
			throw handleError(remote, e.getMessage());
		}
	}

	/**
	 * Sends a request to check if a node is susepcted on the remote node
	 * @param remote PicoAddress object
	 * @param suspect PicoAddress object
	 * @return boolean value
	 * @throws Exception if an error occurs during the call
	 */
	public boolean isSuspect(PicoAddress remote, PicoAddress suspect) throws Exception {
		RpcServiceBlockingStub stub = stubs.get(remote);

		if (stub == null) {
			connectNewHost(remote);
			stub = stubs.get(remote);
		}

		RpcMetadata meta = RpcMetadata.newBuilder()
			.setIp(suspect.getIP())
			.setPort(suspect.getPort())
			.build();

		try {
			return stub.isSuspect(meta).getValue();
		} catch (Exception e) {
			throw handleError(remote, String.format("Failed to send ISSUSPECT to %s: %s", remote, e.getMessage()));
		}
	}

	/**
	 * General error handler for the client
	 * @param remote PicoAddress object
	 * @param msg String message
	 * @return PicoException object
	 */
	private PicoException handleError(PicoAddress remote, String msg) {
		logger.error(msg);
		ManagedChannel channel = channels.get(remote);
		channel.shutdownNow();
		channels.remove(remote);
		stubs.remove(remote);

		return new PicoException(msg);
	}
}
