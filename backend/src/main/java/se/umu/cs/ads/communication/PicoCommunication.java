package se.umu.cs.ads.communication;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.messagehandler.MessageHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PicoCommunication {
    private static final Logger logger = LogManager.getLogger(PicoCommunication.class);
    private final PicoServer server;
    private final InetSocketAddress address;
	private final Set<JMessage> receivedMessages;
	private final ClusterManager cluster;
	private final ExecutorService pool;
	private final PicoClient client;
	private final MessageHandler handler;


    public PicoCommunication(ClusterManager cluster, InetSocketAddress address) {
        this.address = address;
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


	public JMessage sendJMessage(JMessage msg) throws PicoException {
		Future<String> future = this.send(msg);
		String reply = "";
		try {
			reply = future.get();
		} catch (CancellationException | InterruptedException | ExecutionException e) {
				String error = String.format("Received exception while getting reply from %s: %s", 
					address, e.getMessage());
				logger.error(error);
				throw new PicoException(error);
		}

		JMessage responseMessage = JMessage.fromJson(reply);
		if (responseMessage != null)
			return responseMessage;

		throw new PicoException("Successfully received reply but reply was empty");
	}

    private Future<String> send(JMessage msg) {
		if (msg.getSender() == null)
			msg.setSender(address);

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

	public List<JMessage> broadcast(JMessage msg) throws PicoException {
		List<InetSocketAddress> addresses = cluster.getClusterAddresses();
		return broadcast(addresses, msg);
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


	public JMessage receive(JMessage message) {
		//Reliable multicast
		//Have we received this message before, ín that case do nothing
		MessageType type = message.getType();
		InetSocketAddress sender = message.getSender();
		logger.info("Received {} message from {}", type, sender);
		
		if (receivedMessages.contains(message)) {
			logger.info("Message {} from {} has already been received", type, sender);
			return null; //TODO: return JMessage
		}

		receivedMessages.add(message);
		logger.info("{} message from {} has not been received before, broadcasting to other members...", type, sender);
		cluster.broadcast(message);

		//handle message here
		
		return handler.handle(message); //TODO: return JMessage
	}

	/**
	 * When another member wished to leave the cluster
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




















	public JMessage join(JMessage message) {
		return this.cluster.join(message);
	}
}
