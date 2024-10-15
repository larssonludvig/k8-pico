package se.umu.cs.ads.communication;

import com.google.common.util.concurrent.ListenableFuture;

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
    private final PicoAddress address;

    public PicoClient(PicoAddress address) {
		this.address = address;
		this.stubs = new ConcurrentHashMap<>();
		this.channels = new ConcurrentHashMap<>();
    }

	public void connectNewHost(PicoAddress address) {
		String ip = address.getIP();
		int port = address.getPort();
		logger.info("Connecting to {} ...", address);
		ManagedChannel channel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext().build();
		RpcServiceFutureStub stub = RpcServiceGrpc.newFutureStub(channel);
		channels.put(address, channel);
		stubs.put(address, stub);
		logger.info("Connected to {}!", address);
	}	


	public RpcNodes join(PicoAddress remote, RpcJoinRequest msg) throws Exception {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);

		if (stub == null) {
			connectNewHost(remote);
			stub = stubs.get(remote);
		}
		
		logger.info("Sending JOIN_REQUEST to {} ...", remote);
		RpcNodes reply = stub.join(msg).get();
		logger.info("Received reply for JOIN_REQUEST");
		return reply;
	}


	public RpcPerformance fetchPerformance(PicoAddress remote) throws PicoException {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		logger.info("Fetching performance from {}...", remote);		
		try {
			RpcPerformance result = stub.fetchNodePerformance(RpcEmpty.newBuilder().build()).get();
			logger.info("Done fetching performance from {}!", remote);
			return result;
		} catch (Exception e) {
			logger.error("Could not fetch performance from {}: {}", remote, e.getMessage());
			throw new PicoException("Could not fetch performance from: " + remote +": " + e.getMessage());
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

	public Node fetchNode(PicoAddress remote) throws Exception {
        RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);

        RpcMetadata meta = RpcMetadata.newBuilder()
            .setIp(remote.getIP())
            .setPort(remote.getPort())
            .build();

        logger.info("Sending FETCH_NODE to {}...", remote);
        RpcNode reply = stub.fetchNode(meta).get();
        logger.info("Received reply from FETCH_NODE");
        return NodeSerializer.fromRPC(reply);
    }

	public RpcContainerEvaluation evaluateContainer(RpcContainer container, PicoAddress remote) throws Exception {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		logger.info("Sending evaluation request for {} to {} ...", container.getName(), remote);
		RpcContainerEvaluation response = stub.elvaluateContainer(container).get();
		logger.info("Received evaluation reply from {}", remote);
		return response;
	}

	public void containerElectionStart(RpcContainer container, PicoAddress remote) throws PicoException {
		logger.info("Initiating CONTAINER_ELECTION_START for {} to {}", container.getName(), remote);
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		try {
			stub.containerElectionStart(container);
		} catch (Exception e) {
			logger.error("Received exception from CONTAINER_ELECTION_START: {}", e.getMessage());
			throw new PicoException(e.getMessage());
		}
	}

	public void createContainer(RpcContainer container, PicoAddress remote) throws Exception {
		RpcServiceFutureStub stub = addRemoteIfNotConnected(remote);
		logger.info("Sending CREATE_CONTAINER for container {} to {} ...", 
			container.getName(), remote);
		
		stub.createContainer(container);
	}
}
