package se.umu.cs.ads.communication;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.*;
import java.util.concurrent.*;


import se.umu.cs.ads.communication.RpcServiceGrpc.RpcServiceFutureStub;
import se.umu.cs.ads.exception.PicoException;
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
		logger.info("Connecting to {} ...", address);
		ManagedChannel channel = ManagedChannelBuilder
			.forAddress(address.getIP(), address.getPort())
			.usePlaintext()
			.build();

		RpcServiceFutureStub stub = RpcServiceGrpc.newFutureStub(channel);
		channels.put(address, channel);
		stubs.put(address, stub);
		logger.info("Connected to {}!", address);
	}	


	public RpcNodes join(PicoAddress remote, RpcJoinRequest msg) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		
		logger.info("Sending JOIN_REQUEST to {} ...", remote);
		RpcNodes reply = null;
		try {
			reply = stub.join(msg).get();
		} catch (Exception e) {
			String err = String.format("Received error from %s when sending JOIN_REQUEST: %s", remote, e.getMessage());
			logger.error(err);
			throw new PicoException(err);
		}

		logger.info("Received reply for JOIN_REQUEST");
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
		
		try {
			stub.leave(meta);
		} catch (Exception e) {
			String err = String.format("Received error from %s when sending LEAVE: %s", remote, e.getMessage());
			logger.error(err);
			throw new PicoException(err);
		}
		logger.info("Sucessfully sent LEAVE to {}", remote);
	}

	// Request to remove self from remote node
	public void removeNode(PicoAddress remote, PicoAddress self) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);

		RpcMetadata meta = RpcMetadata.newBuilder()
			.setIp(self.getIP())
			.setPort(self.getPort())
			.build();

		logger.info("Sending request to remove node {} to {}...", self, remote);
		try {
			stub.removeNode(meta);
		} catch (Exception e) {
			String err = String.format("Received error when removing node %s: %s", remote, e.getMessage());
			logger.error(err);
			throw new PicoException(err);
		}
	}

	public RpcPerformance fetchPerformance(PicoAddress remote) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		logger.debug("Fetching performance from {}...", remote);		
		try {
			RpcPerformance result = stub.fetchNodePerformance(RpcEmpty.newBuilder().build()).get();
			logger.debug("Done fetching performance from {}!", remote);
			return result;
		} catch (Exception e) {
			String err = String.format("Could not fetch performance from %s: %s", remote, e.getMessage());
			logger.error(err);
			throw new PicoException(err);
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
        try {
			RpcNode reply = stub.fetchNode(meta).get();
        	logger.info("Received reply from FETCH_NODE");
        	return NodeSerializer.fromRPC(reply);
		} catch (Exception e) {
			String err = String.format("Received error from remote %s from FETCH_NODE: %s", remote, e.getMessage());
			logger.info(err);
			throw new PicoException(err);
		}
    }

	public RpcContainerEvaluation evaluateContainer(RpcContainer container, PicoAddress remote) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		logger.info("Sending evaluation request for {} to {} ...", container.getName(), remote);

		try {
			RpcContainerEvaluation res = stub.elvaluateContainer(container).get(); 
			logger.info("Received evaluation reply from {}: {}", remote, res.getScore());
			return res;
		} catch(Exception e) {
			String err = String.format("Received error from remote %s when evaluating container %s: %s",
				remote, container.getName(), e.getMessage());
			logger.error(err);
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
			logger.error(err);
			throw new PicoException(err);
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
			logger.error(err);
			throw new PicoException(err);
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
			stub.containerElectionEnd(msg);
		} catch (Exception e) {
			logger.warn("Failed to send ELECTION_END for container {} to {}: {}", 
				container.getName(), to, e.getMessage());
		}
	}
}
