package se.umu.cs.ads.communication;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;


public class PicoCommunication {
    private static final Logger logger = LogManager.getLogger(PicoCommunication.class);
    private final PicoServer server;
    private final InetSocketAddress address;
	private final Set<JMessage> receivedMessages;
	private final ClusterManager cluster;
	private final ExecutorService pool;
	private final PicoClient client;


    public PicoCommunication(ClusterManager cluster, ExecutorService pool, InetSocketAddress address) {
        this.address = address;

        this.server = new PicoServer(this);
		this.client = new PicoClient(address);
        this.receivedMessages = ConcurrentHashMap.newKeySet();
		this.cluster = cluster;
		this.pool = pool;
		
        try {
            this.server.start();
        } catch (Exception e) {
            logger.error("Failed to rpc start server", e);
        }
    }

    public Future<String> send(JMessage msg) {
		msg.setSender(this.ip + ":" + this.port);

		return pool.submit(() -> {
			return client.send(msg);
		});

    }

	public InetSocketAddress getAddress() {
		return address;
	}

	public void registerNewMember(InetSocketAddress address) {
		client.connectNewHost(address);
		
	}

    public List<JMessage> broadcast(List<InetSocketAddress> addresses, JMessage msg) throws PicoException {

		List<JMessage> messages = new ArrayList<>();
		List<Future<String>> futures = new ArrayList<>();
		List<String> exceptions = new ArrayList<>();
		//Send messages in parallel
		for (int i = 0; i < addresses.size(); i++) {
			InetSocketAddress address = addresses.get(i);
			msg.setDestination(address);
			futures.add(send(msg));
		}
		
		//Wait for result
        for (int i = 0; i < futures.size(); i++) {
			Future<String> future = futures.get(i);
			InetSocketAddress address = addresses.get(i);
			try {
				String res = future.get();
				JMessage reply = JMessage.fromJson(res);
				messages.add(reply);
			} catch (CancellationException | InterruptedException | ExecutionException e) {
				logger.error("Received exception while getting reply from {}: {}", address, e.getMessage());
				exceptions.add(e.getMessage());
			}
		}

		if (exceptions.isEmpty())
			return messages;

		StringBuilder builder = new StringBuilder();
		builder.append("Encountered " + exceptions.size() + " error while waiting for reply:\n");
		
		for (String errorMsg : exceptions) 
			builder.append("\t").append(errorMsg).append("\n");
		
		throw new PicoException(builder.toString());
      
    }


	public void receive(JMessage message) {
		//Reliable multicast
		//Have we received this message before, ín that case do nothing
		MessageType type = message.getType();
		String sender = message.getSender();
		logger.info("Received {} message from {}", type, sender);
		
		if (receivedMessages.contains(message)) {
			logger.info("Message {} from {} has already been received", type, sender);
			return;
		}

		receivedMessages.add(message);
		logger.info("{} message from {} has not been received before, broadcasting to other members...", type, sender);
		cluster.broadcast(message);
		cluster.receive(message);
	}
}
