package se.umu.cs.ads.communication;

import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;


import se.umu.cs.ads.communication.RpcServiceGrpc.RpcServiceFutureStub;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PicoClient {
	private final static Logger logger = LogManager.getLogger(PicoClient.class);
	private final Map<InetSocketAddress, ManagedChannel> channels;
	private final Map<InetSocketAddress, RpcServiceFutureStub> stubs;
    private final InetSocketAddress address;

    public PicoClient(InetSocketAddress address) {
		this.address = address;
		this.stubs = new ConcurrentHashMap<>();
		this.channels = new ConcurrentHashMap<>();
    }

	public void connectNewHost(InetSocketAddress address) {
		String ip = address.getAddress().getHostAddress();
		int port = address.getPort();
		ManagedChannel channel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext().build();
		RpcServiceFutureStub stub = RpcServiceGrpc.newFutureStub(channel);
		channels.put(address, channel);
		stubs.put(address, stub);
		logger.info("Connected to {}!", address);
	}	


	public RpcNodes join(RpcNode msg) throws Exception {
		String ip = msg.getIp();
		int port = msg.getPort();
		InetSocketAddress address = new InetSocketAddress(ip, port);
		RpcServiceFutureStub stub = stubs.get(address);

		if (stub == null) {
			connectNewHost(address);
			stub = stubs.get(address);
		}
		
		logger.info("Sending JOIN_REQUEST to {} ...", address);
		RpcNodes reply = stub.join(msg).get();
		logger.info("Received reply for JOIN_REQUEST");
		return reply;
	}

    // public String send(JMessage msg) throws RuntimeException {
    //     RpcMessage rpcMessage = RpcMessage.newBuilder().setPayload(msg.toString()).build();
	// 	RpcServiceFutureStub stub = stubs.get(msg.getDestination());

	// 	if (stub == null) {
	// 		logger.warn("Trying to send {} message to {} but client is not connected. Trying to connect ...",
	// 			msg.getType(), msg.getDestination());
	// 		connectNewHost(msg.getDestination());
	// 		stub = stubs.get(msg.getDestination());
	// 	}

	// 	logger.info("Sending {} to remote {}", msg.getType(), msg.getDestination());
	// 	// @SuppressWarnings("null")
	// 	ListenableFuture<RpcMessage> future = stub.send(rpcMessage);

    //     try {
    //         return future.get().getPayload();
    //     } catch (InterruptedException | CancellationException | ExecutionException e) {
	// 		String error = String.format("Received interrupt while waiting for rpc message with cause: %s", e.getMessage());
	// 		logger.error(error);
    //         throw new PicoException(error);
    //     }
    // }

	
}
