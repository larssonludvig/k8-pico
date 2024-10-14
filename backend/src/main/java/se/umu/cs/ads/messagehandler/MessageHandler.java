package se.umu.cs.ads.messagehandler;

import java.util.*;
import java.util.concurrent.*;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.resolver.InetSocketAddressResolver;
import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.clustermanagement.ClusterManager;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;

public class MessageHandler {
    private final static Logger logger = LogManager.getLogger(MessageHandler.class);
	private final static double NAME_CONFLICT = -1.0;
	private final static double PORT_CONFLICT = -2.0;
    private final Map<String, PicoContainer> candidates = new ConcurrentHashMap<>();
    private NodeManager nodeManager;
    private ClusterManager cluster;
    
    public MessageHandler(NodeManager nodeManager, ClusterManager cluster) {
        this.nodeManager = nodeManager;
        this.cluster = cluster;
    }

    public JMessage handle(JMessage jmsg) throws PicoException {
		MessageType type = jmsg.getType();
		Object payloadClass = jmsg.getPayload();
		if (!MessageVerifier.hasCorrectPayload(type, payloadClass)) {
  		 	String err = String.format("Message was %s but payload was not correct. Ignoring", type);
            logger.error(err);
            return new JMessage()
                .setType(MessageType.ERROR)
                .setPayload(err);
		}
	    	
		switch (type) {

			case ERROR:
				throw new PicoException(jmsg.getPayload().toString());
			
            case FETCH_NODE:
                InetSocketAddress ipPort = (InetSocketAddress) jmsg.getPayload();

                return new JMessage()
                    .setPayload(this.nodeManager.getNode(ipPort));

            case FETCH_NODES:
                return new JMessage()
                    .setPayload(this.nodeManager.getNode());

            case FETCH_NODE_PERFORMANCE:
                double cpuLoad = this.nodeManager.getCPULoad();
                double memLoad = this.nodeManager.getMemLoad();

                return new JMessage()
                    .setPayload(new Performance(cpuLoad, memLoad));

            case CREATE_CONTAINER:
                return createContainer(jmsg);

            /**
            Scenarion: ny nod joinar
            1. Alla andra noder skickar sina containrar
            */

            /**
            Scenario: Spawna en container
            1. Noden som får begäran skickar CONTAINER_ELECTION_START, X
            2. leadern broadcastar ut till alla noder
            3. Noderna evaluerar begäran och skickar svar EVALUATE_CONTAINER_REQUEST
                3.1. Noderna kollar för konflikter (namn, port)
                3.2. Noderna beräknar/skickar sin score+svar
            4. Ledaren väljer nod utifrån score/svar och skickar CONTAINER_START
            5. Den nod som startar containrar broadcastar detta till alla CONTAINER_ELECTION_END (Innehåller info om ny container)
                
            */
            case CONTAINER_LIST:
                Object o = jmsg.getPayload();
                try {
                    InetSocketAddress sender = jmsg.getSender();
                    List<PicoContainer> containers = (List<PicoContainer>) o;
                    logger.info("Received {} containers from {}", containers.size(), sender);
                    //TODO: update info about hosts
                    this.nodeManager.updateRemoteContainers(sender, containers);
                } catch(Exception e) {
                    String err = "Received CONTAINER_LIST but payload was not list of containers";
                    logger.error(err);
                    return new JMessage()
                        .setType(MessageType.ERROR)
                        .setPayload(err);
                }

                return new JMessage()
                    .setType(MessageType.EMPTY);

            case CONTAINER_ELECTION_START:
                return container_election_start(jmsg);

            case EVALUATE_CONTAINER_REQUEST:
                return evaluate_container_request(jmsg);

            case CONTAINER_ELECTION_END:
                return container_election_end(jmsg);

			case JOIN_REQUEST:
				return join_request(jmsg);
			case LEAVE_REQUEST:
				return leave_request();
            default:  
                return new JMessage()
					.setType(MessageType.ERROR)
					.setPayload("Unknown message type: " + jmsg.getType());
        }
    }



	private JMessage join_request(JMessage msg) {
		List<Node> members = cluster.getClusterMembers();
		return new JMessage().setPayload(members).setType(MessageType.JOIN_REQUEST);
	}

	private JMessage leave_request() {
		return null;
	}
    private JMessage createContainer(JMessage msg) {
        Object payload = msg.getPayload();
        if (!(payload instanceof PicoContainer)) {
            String err = "Message was CREATE_CONTAINER but payload was not a container. Ignoring";
            logger.error(err);
            return new JMessage()
                .setType(MessageType.ERROR)
                .setPayload(err);
        }

        PicoContainer container = (PicoContainer) payload;

        try {
            container = nodeManager.createLocalContainer(container);
            container = nodeManager.startContainer(container.getName());
        } catch (Exception e) {
            String err = String.format("Failed to create and run container: %s", e.getMessage());
            logger.error(err);
            return new JMessage()
                .setType(MessageType.ERROR)
                .setPayload(err);
        }

        JMessage reply = new JMessage()
            .setSender(nodeManager.getAddress())
            .setType(MessageType.CONTAINER_ELECTION_END)
            .setPayload(container);


        try {
            this.nodeManager.broadcast(reply);
        } catch (Exception e) {
            String err = String.format("Failed to broadcast ELECTION_END: %s", e.getMessage());
            logger.error(err);
            return new JMessage()
                .setType(MessageType.ERROR)
                .setPayload(err);
        }

        return new JMessage()
            .setType(MessageType.EMPTY)
            .setPayload("Successfully created and started container"); 
    }

    private JMessage container_election_end(JMessage msg) {
        Object payload = msg.getPayload();
        if (!(payload instanceof PicoContainer)) {
			String error = "Received ELECTION_END but payload not instance of container!"; 
            logger.error(error);
            return  JMessage.ERROR(error);
        }

        PicoContainer container = (PicoContainer) payload;
        InetSocketAddress sender = msg.getSender();
        nodeManager.updateRemoteContainers(sender, container);
        candidates.remove(container.getName());

        return new JMessage();
    }

    private JMessage evaluate_container_request(JMessage msg) {
        Object o = msg.getPayload();

        if (!(o instanceof PicoContainer)) {
			String error = "Message was EVALUATE_CONTAINER_REQUETS but payload was not a container"; 
            logger.error(error);
            return JMessage.ERROR(error);
        }

        PicoContainer container = (PicoContainer) o;
        String name = container.getName();
        double score = nodeManager.getScore();

        //check confliting names
        if (nodeManager.hasContainerName(name) || candidates.containsKey(name)) {
            logger.warn("Container with name {} already occupied.", name);
            return new JMessage()
                .setPayload(NAME_CONFLICT)
                .setSender(nodeManager.getAddress())
                .setType(MessageType.EMPTY);
        }
        
        //check conflicting ports
        String conflictingPort = nodeManager.hasContainerPort(container.getPorts());
        if (conflictingPort != null) {
            logger.warn("New container with name {} has port conflict: {} is already used", name, conflictingPort);
            return new JMessage()
                .setPayload(PORT_CONFLICT)
                .setSender(nodeManager.getAddress())
                .setType(MessageType.EMPTY);
        }

        logger.info("Score evaluated to: {}", score);
        
        return new JMessage()
            .setPayload(score)
            .setSender(nodeManager.getAddress())
            .setType(MessageType.EMPTY);
    }

    private JMessage container_election_start(JMessage msg) {
        Object o = msg.getPayload();
        if (!(o instanceof PicoContainer)) {
            String err = "Received CONTAINER_ELECTION_START but payload was not a container!";
            logger.error(err);
            return JMessage.ERROR(err);
		}

        PicoContainer container = (PicoContainer) o;
        JMessage newMsg = new JMessage()
            .setSender(nodeManager.getAddress())
            .setPayload(container)
            .setType(MessageType.EVALUATE_CONTAINER_REQUEST);
        

		List<JMessage> replies;
		try {
			replies = this.nodeManager.broadcast(newMsg).stream()
				.map(obj -> (JMessage) obj)
				.toList();
		} catch (Exception e) {
            String err = "Failed to broadcast messages: " + e.getMessage();
			logger.error(err);
            return JMessage.ERROR(err);
			
		}

		double minScore = Double.MAX_VALUE;
		InetSocketAddress minAddr = null;
		for (JMessage reply : replies) {
			double score = (Double) reply.getPayload();
			logger.info("Received reply from {} with score {}", reply.getSender(), score);
			if (score < minScore) {
				minScore = score;
				minAddr = reply.getSender();
			}
		}

		String name = container.getName();
		if (minScore == NAME_CONFLICT || candidates.containsKey(name)) {
			container.setState(PicoContainerState.NAME_CONFLICT);
			logger.warn("Container {} has name conflict, it will not be started!", name);
		}
		else if (minScore == PORT_CONFLICT) {
			container.setState(PicoContainerState.PORT_CONFLICT);
			logger.warn("Container {} has port conflicts, it will not be started!", name);
		}

		JMessage reply = new JMessage()
			.setSender(nodeManager.getAddress())
			.setPayload(container)
			.setType(MessageType.CREATE_CONTAINER)
			.setDestination(minAddr);

		//mark container as candidate
		candidates.put(name, container);

		logger.info("Container election finished for {} finished, sending result to {}", name, minAddr);
		this.nodeManager.send(reply);
		return new JMessage()
            .setPayload(container);
    }
}
