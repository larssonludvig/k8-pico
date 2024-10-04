package se.umu.cs.ads.nodemanager;

import java.util.HashMap;
import java.util.List;

import org.jgroups.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgroups.Address;
import org.jgroups.BytesMessage;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.MessageDispatcher;

import se.umu.cs.ads.types.*;
import se.umu.cs.ads.containerengine.ContainerEngine;
import se.umu.cs.ads.exception.PicoException;

public class NodeDispatcher implements RequestHandler {
	private final static double NAME_CONFLICT = -1.0;
	private final static double PORT_CONFLICT = -2.0;
    private MessageDispatcher disp;
    private NodeManager nodeManager;
	private final static Logger logger = LogManager.getLogger(NodeDispatcher.class);

    public NodeDispatcher initialize(MessageDispatcher disp, NodeManager nodeManager) {
        this.disp = disp;
        this.nodeManager = nodeManager;
        return this;
    }

    public List<Object> broadcast(Object obj) throws Exception {
        return this.disp.castMessage(
            null,
            new BytesMessage(null, obj),
            RequestOptions.SYNC()
        ).getResults();
    }

    public Object send(Address adr, Object obj) throws Exception {
        return this.disp.sendMessage(
            new BytesMessage(adr, obj),
            RequestOptions.SYNC()
        );
    }

    @Override
    public Object handle(Message msg) throws Exception {
        if (msg.getObject() instanceof JMessage) {
            JMessage jmsg = (JMessage) msg.getObject();

            switch (jmsg.getType()) {
                case FETCH_NODE:
                    String name = (String) jmsg.getPayload();
                    return this.nodeManager.getNode(name);

                case FETCH_NODES:
					//fetch active containers
                    return this.nodeManager.getNode();

                case FETCH_CONTAINER_NAMES:
                    return "FETCH_CONTAINER_NAMES, not implemented.";

                case CREATE_CONTAINER:
                    ContainerEngine engine = new ContainerEngine();
                    PicoContainer container = (PicoContainer) jmsg.getPayload();

                    try {
                        container = engine.createContainer(container.getImage(), container.getName());
                        container = engine.runContainer(container.getName());
                        return "Container started sucessfully!";
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return "Failed to create container.";

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
					String sender = jmsg.getSender();
					try {
						List<PicoContainer> containers = (List<PicoContainer>) o;
						logger.info("Received {} containers from {}", containers.size(), sender);
						//TODO: update info about hosts
						nodeManager.updateRemoteContainers(sender, containers);
                        return null;
					} catch(Exception e) {
						logger.error("Received CONTAINER_LIST but payload was not list of containers");
					}
					
					return null;

				case CONTAINER_ELECTION_START:
					container_election_start(jmsg);
                    return null;

                case EVALUATE_CONTAINER_REQUEST:
                    return evaluate_container_request(jmsg);

                case EMPTY:
                    return "EMPTY, not implemented.";

                default:  
                    return "Unknown message type: " + jmsg.getType() + ".";
            }
        }
        return "Unknown object type.";
    }

	private Object evaluate_container_request(JMessage msg) {
		Object o = msg.getPayload();

		if (!(o instanceof PicoContainer)) {
			logger.error("Message was EVALUATE_CONTAINER_REQUETS but payload was not a container");
			return null;
		}

		PicoContainer container = (PicoContainer) o;
		String name = container.getName();
		double score = nodeManager.getScore();
				//check conflicting ports
				//check confliting names
		if (nodeManager.hasContainerName(name)) {
			logger.warn("Container with name {} already occupied.", name);
			return NAME_CONFLICT;
		}

		String conflictingPort = nodeManager.hasContainerPort(container.getPorts());
		if ( conflictingPort == null) {
			logger.warn("New container with name {} has port conflict: {} is already used", name);
            return PORT_CONFLICT;
		}

		logger.info("Score evaluted to: {}", score);
		JMessage reply = new JMessage()
			.setPayload(score)
			.setSender(nodeManager.getChannelAddress())
			.setType(MessageType.EMPTY);

		return reply;
	}

	private void container_election_start(JMessage msg) {
		Object o = msg.getPayload();
		if (!(o instanceof PicoContainer)) {
			logger.error("Received CONTAINER_ELECTION_START but payload was not a container!");
			return;
		}

		PicoContainer container = (PicoContainer) o;
		JMessage newMsg = new JMessage()
			.setSender(nodeManager.getChannelAddress())
			.setPayload(container)
			.setType(MessageType.EVALUATE_CONTAINER_REQUEST);
		
		try {
            List<JMessage> replies = broadcast(newMsg).stream()
                .map(obj -> (JMessage) obj)
				// .map(obj -> (Double) obj.getPayload())
                .toList();
    
			double minScore = 0;
			String minAddr = null;
			for (JMessage reply : replies) {
				double score = (Double) reply.getPayload();
                if (score < minScore) {
					minScore = score;
					minAddr = reply.getSender();
				}
            }

			//show GUI error
			// if (score == PORT_CONFLICT)
			// else if (score == NAME_CONFLICT)

			
			JMessage reply = new JMessage()
				.setSender(nodeManager.getChannelAddress())
				.setPayload(container)
				.setType(MessageType.CREATE_CONTAINER);

			send(nodeManager.getAddressOfNode(minAddr), reply);

		} catch (Exception e) {
			logger.error("Could not broadcast message: {}", e.getMessage());
		}
	}
}