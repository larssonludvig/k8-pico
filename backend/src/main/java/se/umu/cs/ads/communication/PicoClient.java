package se.umu.cs.ads.communication;

import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import se.umu.cs.ads.types.PicoAddress;
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
		stub.leave(meta);
	}

	// Request to remove self from remote node
	public void removeNode(PicoAddress remote, PicoAddress self) throws Exception {
		RpcServiceFutureStub stub = stubs.get(remote);
		if (stub == null) {
			connectNewHost(remote);
			stub = stubs.get(remote);
		}

		RpcMetadata meta = RpcMetadata.newBuilder()
			.setIp(self.getIP())
			.setPort(self.getPort())
			.build();

		logger.info("Sending request to remove node {} to {}...", self, remote);
		stub.removeNode(meta);
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
        RpcServiceFutureStub stub = stubs.get(remote);

        if (stub == null) {
            connectNewHost(remote);
            stub = stubs.get(remote);
        }

        RpcMetadata meta = RpcMetadata.newBuilder()
            .setIp(remote.getIP())
            .setPort(remote.getPort())
            .build();

        logger.info("Sending FETCH_NODE to {}...", remote);
        RpcNode reply = stub.fetchNode(meta).get();
        logger.info("Received reply from FETCH_NODE");
        return NodeSerializer.fromRPC(reply);
    }

	public Node heartBeat(PicoAddress remote) throws Exception {
		RpcServiceFutureStub stub = stubs.get(remote);

		if (stub == null) {
			connectNewHost(remote);
			stub = stubs.get(remote);
		}

		try {
			Node node = NodeSerializer.fromRPC(stub.heartBeat(RpcEmpty.newBuilder().build()).get());
			logger.debug("Successfully sent HEARTBEAT to {}", remote);
			return node;
		} catch (Exception e) {
			logger.error("Failed to send HEARTBEAT to {}: {}", remote, e.getMessage());
			throw new PicoException("Failed to send HEARTBEAT to " + remote + ": " + e.getMessage());
		}
	}
}
