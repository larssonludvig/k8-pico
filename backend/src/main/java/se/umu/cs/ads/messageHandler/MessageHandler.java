package se.umu.cs.ads.messagehandler;

import java.util.*;
import java.util.concurrent.*;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.types.*;

public class MessageHandler {
    private final static Logger logger = LogManager.getLogger(MessageHandler.class);
	private final static double NAME_CONFLICT = -1.0;
	private final static double PORT_CONFLICT = -2.0;
    private final Map<String, PicoContainer> candidates = new ConcurrentHashMap<>();
    private NodeManager nodeManager;
    
    public MessageHandler(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    public Object handle(JMessage jmsg) {
        switch (jmsg.getType()) {
            case FETCH_NODE:
                InetSocketAddress ipPort = (InetSocketAddress) jmsg.getPayload();
                return this.nodeManager.getNode(ipPort);

            case FETCH_NODES:
                return this.nodeManager.getNode();

            case FETCH_NODE_PERFORMANCE:
                double cpuLoad = this.nodeManager.getCPULoad();
                double memLoad = this.nodeManager.getMemLoad();
                return new Performance(cpuLoad, memLoad);

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
                    String sender = jmsg.getSender();
                    List<PicoContainer> containers = (List<PicoContainer>) o;
                    logger.info("Received {} containers from {}", containers.size(), sender);
                    //TODO: update info about hosts
                    this.nodeManager.updateRemoteContainers(sender, containers);
                    return null;
                } catch(Exception e) {
                    logger.error("Received CONTAINER_LIST but payload was not list of containers");
                }
                
                return null;

            case CONTAINER_ELECTION_START:
                return container_election_start(jmsg);

            case EVALUATE_CONTAINER_REQUEST:
                return evaluate_container_request(jmsg);

            case CONTAINER_ELECTION_END:
                return container_election_end(jmsg);

            default:  
                return "Unknown message type: " + jmsg.getType() + ".";
        }
    }

    private String createContainer(JMessage msg) {
        Object payload = msg.getPayload();
        if (!(payload instanceof PicoContainer)) {
            logger.error("Message was CREATE_CONTAINER but payload was not a container. Ignoring");
            return null;
        }

        PicoContainer container = (PicoContainer) payload;

        try {
            container = nodeManager.createLocalContainer(container);
            container = nodeManager.startContainer(container.getName());
        } catch (Exception e) {
            String res = String.format("Failed to create and run container: %s", e.getMessage());
            logger.error(res);
            return res;
        }

        JMessage reply = new JMessage()
            .setSender(nodeManager.getChannelAddress())
            .setType(MessageType.CONTAINER_ELECTION_END)
            .setPayload(container);


        try {
            this.nodeManager.broadcast(reply);
        } catch (Exception e) {
            String res = String.format("Failed to broadcast ELECTION_END: %s", e.getMessage());
            logger.error(res);
            return res;
        }

        return "Successfully created and started container";
    }

    private String container_election_end(JMessage msg) {
        Object payload = msg.getPayload();
        if (!(payload instanceof PicoContainer)) {
            logger.error("Received ELECTION_END but payload not instance of container!");
            return "Failed to update remote containers after election.";
        }
        PicoContainer container = (PicoContainer) payload;
        String sender = msg.getSender();
        nodeManager.updateRemoteContainers(sender, container);

        candidates.remove(container.getName());

        return "Successfully updated remote containers after election.";
    }

    private JMessage evaluate_container_request(JMessage msg) {
        Object o = msg.getPayload();

        if (!(o instanceof PicoContainer)) {
            logger.error("Message was EVALUATE_CONTAINER_REQUETS but payload was not a container");
            return null;
        }

        PicoContainer container = (PicoContainer) o;
        String name = container.getName();
        double score = nodeManager.getScore();

        //check confliting names
        if (nodeManager.hasContainerName(name)) {
            logger.warn("Container with name {} already occupied.", name);
            return NAME_CONFLICT;
        }
        
        //check conflicting ports
        String conflictingPort = nodeManager.hasContainerPort(container.getPorts());
        if (conflictingPort != null) {
            logger.warn("New container with name {} has port conflict: {} is already used", name, conflictingPort);
            return PORT_CONFLICT;
        }

        logger.info("Score evaluted to: {}", score);
        
        JMessage reply = new JMessage()
            .setPayload(score)
            .setSender(nodeManager.getChannelAddress())
            .setType(MessageType.EMPTY);

        return reply;
    }

    private PicoContainer container_election_start(JMessage msg) {
        Object o = msg.getPayload();
        if (!(o instanceof PicoContainer)) {
            logger.error("Received CONTAINER_ELECTION_START but payload was not a container!");
            return null;
        }

        PicoContainer container = (PicoContainer) o;
        JMessage newMsg = new JMessage()
            .setSender(nodeManager.getChannelAddress())
            .setPayload(container)
            .setType(MessageType.EVALUATE_CONTAINER_REQUEST);
        

        try {
            Future<List<JMessage>> future = nodeManager.getPool().submit(() -> {
                return this.nodeManager.broadcast(newMsg).stream()
                    .map(obj -> (JMessage) obj)
                    .toList();
            });
            
            try {
                List<JMessage> replies = future.get();

                double minScore = Double.MAX_VALUE;
                String minAddr = null;
                for (JMessage reply : replies) {
                    double score = (Double) reply.getPayload();
                    logger.info("Received reply from {} with score {}", reply.getSender(), score);
                    if (score < minScore) {
                        minScore = score;
                        minAddr = reply.getSender();
                    }
                }
    
                String name = container.getName();
                if (minScore == NAME_CONFLICT ) {
                    container.setState(PicoContainerState.NAME_CONFLICT);
                    logger.warn("Container {} has name conflict, it will not be started!", name);
                }
                else if (minScore == PORT_CONFLICT || candidates.containsKey(name)) {
                    container.setState(PicoContainerState.PORT_CONFLICT);
                    logger.warn("Container {} has port conflicts, it will not be started!", name);
                }
    
                JMessage reply = new JMessage()
                    .setSender(nodeManager.getChannelAddress())
                    .setPayload(container)
                    .setType(MessageType.CREATE_CONTAINER);
    
                //mark container as candidate
                candidates.put(name, container);
    
                logger.info("Container election finished for {} finished, sending result to {}", name, minAddr);
                this.nodeManager.send(nodeManager.getAddressOfNode(minAddr), reply);
    
                return container;
            } catch (Exception e) {
                logger.error("Failed to get replies from nodes: {}", e.getMessage());
                return null;
            }
    
        } catch (Exception e) {
            logger.error("Could not broadcast message: {}", e.getMessage());
            return null;
        }
    }
}
