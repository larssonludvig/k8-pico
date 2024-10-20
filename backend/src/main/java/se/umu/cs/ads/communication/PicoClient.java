package se.umu.cs.ads.communication;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.*;
import java.util.concurrent.*;


import se.umu.cs.ads.communication.RpcServiceGrpc.RpcServiceFutureStub;
import se.umu.cs.ads.exception.NameConflictException;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.exception.PortConflictException;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.serializers.*;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PicoClient {
	private final static Logger logger = LogManager.getLogger(PicoClient.class);
	private final Map<PicoAddress, ManagedChannel> channels;
	private final Map<PicoAddress, RpcServiceFutureStub> stubs;

    public PicoClient() {
		this.stubs = new ConcurrentHashMap<>();
		this.channels = new ConcurrentHashMap<>();
    }

	public void connectNewHost(PicoAddress address) {
		ManagedChannel channel = ManagedChannelBuilder
			.forAddress(address.getIP(), address.getPort())
			.usePlaintext()
			.build();

		RpcServiceFutureStub stub = RpcServiceGrpc.newFutureStub(channel);
		channels.put(address, channel);
		stubs.put(address, stub);
		logger.info("Connected to new host: {}!", address);
	}	


	public RpcNodes join(PicoAddress remote, RpcJoinRequest msg) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		
		logger.info("Sending JOIN_REQUEST to {} ...", remote);
		long start = System.currentTimeMillis();
		RpcNodes reply = null;
		try {
			reply = stub.join(msg).get();
		} catch (Exception e) {
			String err = String.format("Received error from %s when sending JOIN_REQUEST: %s", remote, e.getMessage());
			throw handleError(remote, err);
		}
		long end = System.currentTimeMillis();
		logger.info("Received reply for JOIN_REQUEST after {} ms", end - start);
		return reply;
	}

	// Request to remove a node from the network
	public void leave(PicoAddress remote) throws Exception {
		RpcServiceFutureStub stub = stubs.get(remote);
		
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

	// Request to remove self from remote node
	public void removeNode(PicoAddress remote, PicoAddress self) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);

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

	public RpcPerformance fetchPerformance(PicoAddress remote) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		logger.info("Fetching performance from {}...", remote);		
		try {
			long start = System.currentTimeMillis();
			RpcPerformance result = stub.fetchNodePerformance(RpcEmpty.newBuilder().build()).get();
			long time = System.currentTimeMillis() - start;
			logger.info("Done fetching performance from {} after {} ms", remote, time);
			return result;
		} catch (Exception e) {
			String err = String.format("Could not fetch performance from %s: %s", remote, e.getMessage());
			throw handleError(remote, err);
		}
	}

	private RpcServiceFutureStub addRemoteIfNotConnected(PicoAddress remote) {
		RpcServiceFutureStub stub = stubs.get(remote);
		if (stub == null) {
			connectNewHost(remote);
			stub = stubs.get(remote);
		}
		return stub; 
	}

	public Node fetchNode(PicoAddress remote) throws PicoException {
        RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);

        RpcMetadata meta = RpcMetadata.newBuilder()
            .setIp(remote.getIP())
            .setPort(remote.getPort())
            .build();

        logger.info("Sending FETCH_NODE to {}...", remote);
		long start = System.currentTimeMillis();
        try {
			RpcNode reply = stub.fetchNode(meta).get();
			long time = System.currentTimeMillis() - start;
        	logger.info("Received reply from FETCH_NODE after {} ms", time);
        	return NodeSerializer.fromRPC(reply);
		} catch (Exception e) {
			String err = String.format("Received error from remote %s from FETCH_NODE: %s", remote, e.getMessage());
			throw handleError(remote, err);
		}
    }

	public RpcContainerEvaluation evaluateContainer(RpcContainer container, PicoAddress remote) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		logger.info("Sending evaluation request for {} to {} ...", container.getName(), remote);
		long start = System.currentTimeMillis();
		try {
			RpcContainerEvaluation res = stub.elvaluateContainer(container).get(); 
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

	public void containerElectionStart(RpcContainer container, PicoAddress remote) throws PicoException {
		logger.info("Initiating CONTAINER_ELECTION_START for {} to {}", container.getName(), remote);
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		try {
			stub.containerElectionStart(container);
		} catch (Exception e) {
			String err = String.format("Received exception from CONTAINER_ELECTION_START: %s", e.getMessage());
			throw handleError(remote, err);
		}
	}

	public void createContainer(RpcContainer container, PicoAddress remote) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
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

	public void markElectionEnd(RpcContainer container, PicoAddress self, PicoAddress to) throws PicoException {
		RpcMetadata rpcSelf = RpcMetadata.newBuilder().setIp(self.getIP()).setPort(self.getPort()).build();
		RpcContainerElectionEnd msg = RpcContainerElectionEnd.newBuilder()
			.setContainer(container)
			.setSender(rpcSelf)
			.build();

		RpcServiceFutureStub stub = addRemoteIfNotConnected(to);
		try {
			logger.info("Sending ELECTION_END for container {} to {} ...", container.getName(), to);
			stub.containerElectionEnd(msg);
		} catch (Exception e) {
			throw handleError(to, 
				String.format("Failed to send ELECTION_END for container %s to %s: %s",
					container.getName(), to, e.getMessage()));
		
		}
	}

	public String sendContainerCommand(RpcContainerCommand command, PicoAddress remote) {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		try {

			logger.info("Sending {} command to {} for container {}", 
				command.getCommand().toString(), remote, command.getContainer().getName());
			return stub.containerCommand(command).get().getPayload();
		} catch (Exception e) {
			throw handleError(remote, e.getMessage());
		}
	}

	public Node heartbeat(PicoAddress remote) throws Exception {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);

		try {
			long start = System.currentTimeMillis();
			Node node = NodeSerializer.fromRPC(stub.heartbeat(RpcEmpty.newBuilder().build()).get());
			long time = System.currentTimeMillis() - start;
			logger.info("Successfully sent HEARTBEAT to {} after {} ms", remote, time);
			return node;
		} catch (Exception e) {
			throw handleError(remote, e.getMessage());
		}
	}

	public boolean isSuspect(PicoAddress remote, PicoAddress suspect) throws Exception {
		RpcServiceFutureStub stub = stubs.get(remote);

		if (stub == null) {
			connectNewHost(remote);
			stub = stubs.get(remote);
		}

		RpcMetadata meta = RpcMetadata.newBuilder()
			.setIp(suspect.getIP())
			.setPort(suspect.getPort())
			.build();

		try {
			return stub.isSuspect(meta).get().getValue();
		} catch (Exception e) {
			throw handleError(remote, String.format("Failed to send ISSUSPECT to %s: %s", remote, e.getMessage()));
		}
	}



	private PicoException handleError(PicoAddress remote, String msg) {
		logger.error(msg);
		ManagedChannel channel = channels.get(remote);
		channel.shutdownNow();
		channels.remove(remote);
		stubs.remove(remote);

		return new PicoException(msg);
	}



}
