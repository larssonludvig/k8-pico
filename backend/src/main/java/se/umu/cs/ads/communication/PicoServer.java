package se.umu.cs.ads.communication;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.umu.cs.ads.types.*;

public class PicoServer {
    private final static Logger logger = LogManager.getLogger(PicoServer.class);
    private PicoCommunication comm;
    private final int port;

    public PicoServer(PicoCommunication comm, int port) {
        this.comm = comm;
        this.port = port;
    }

    public void start() throws Exception {
        Server server = ServerBuilder.forPort(port)
            .addService(new RpcService())
            .build()
            .start();
    }

    public class RpcService extends RpcServiceGrpc.RpcServiceImplBase {
        @Override
        public void send(RpcMessage msg, StreamObserver<RpcMessage> responseObserver) {
            // Deserialize the message
            JMessage jmsg;

            // switch (jmsg.getType()) {
            //     case FETCH_NODE:
            //         String name = (String) jmsg.getPayload();
            //         return this.nodeManager.getNode(name);

            //     case FETCH_NODES:
			// 		//fetch active containers
            //         return this.nodeManager.getNode();

			// 	case FETCH_NODE_PERFORMANCE:
			// 		double cpuLoad = this.nodeManager.getCPULoad();
			// 		double memLoad = this.nodeManager.getMemLoad();
			// 		return new Performance(cpuLoad, memLoad);

            //     case CREATE_CONTAINER:
			// 		return createContainer(jmsg);

			// 	/**
			// 	Scenarion: ny nod joinar
			// 	1. Alla andra noder skickar sina containrar
			// 	*/

			// 	/**
			// 	Scenario: Spawna en container
			// 	1. Noden som får begäran skickar CONTAINER_ELECTION_START, X
			// 	2. leadern broadcastar ut till alla noder
			// 	3. Noderna evaluerar begäran och skickar svar EVALUATE_CONTAINER_REQUEST
			// 		3.1. Noderna kollar för konflikter (namn, port)
			// 		3.2. Noderna beräknar/skickar sin score+svar
            //     4. Ledaren väljer nod utifrån score/svar och skickar CONTAINER_START
			// 	5. Den nod som startar containrar broadcastar detta till alla CONTAINER_ELECTION_END (Innehåller info om ny container)
				 
			// 	*/
			// 	case CONTAINER_LIST:
			// 		Object o = jmsg.getPayload();
			// 		String sender = jmsg.getSender();
			// 		try {
			// 			List<PicoContainer> containers = (List<PicoContainer>) o;
			// 			logger.info("Received {} containers from {}", containers.size(), sender);
			// 			//TODO: update info about hosts
			// 			nodeManager.updateRemoteContainers(sender, containers);
            //             return null;
			// 		} catch(Exception e) {
			// 			logger.error("Received CONTAINER_LIST but payload was not list of containers");
			// 		}
					
			// 		return null;

			// 	case CONTAINER_ELECTION_START:
			// 		return container_election_start(jmsg);

            //     case EVALUATE_CONTAINER_REQUEST:
            //         return evaluate_container_request(jmsg);

			// 	case CONTAINER_ELECTION_END:
			// 		return container_election_end(jmsg);

            //     default:  
            //         return "Unknown message type: " + jmsg.getType() + ".";
            // }
        }

    //     private Object createContainer(JMessage msg) {
    //         Object payload = msg.getPayload();
    //         if (!(payload instanceof PicoContainer)) {
    //             logger.error("Message was CREATE_CONTAINER but payload was not a container. Ignoring");
    //             return null;
    //         }
    
    //         PicoContainer container = (PicoContainer) payload;
    
    //         try {
    //             container = controller.createLocalContainer(container);
    //             container = controller.startContainer(container.getName());
    //         } catch (Exception e) {
    //             String res = String.format("Failed to create and run container: %s", e.getMessage());
    //             logger.error(res);
    //             return res;
    //         }
    
    //         JMessage reply = new JMessage()
    //             .setSender(nodeManager.getChannelAddress())
    //             .setType(MessageType.CONTAINER_ELECTION_END)
    //             .setPayload(container);
    
    
    //         try {
    //             broadcast(reply);
    //         } catch (Exception e) {
    //             String res = String.format("Failed to broadcast ELECTION_END: %s", e.getMessage());
    //             logger.error(res);
    //             return res;
    //         }
    
    //         return "Successfully created and started container";
    //     }
    
    //     private Object container_election_end(JMessage msg) {
    //         Object payload = msg.getPayload();
    //         if (!(payload instanceof PicoContainer)) {
    //             logger.error("Received ELECTION_END but payload not instance of container!");
    //             return null;
    //         }
    //         PicoContainer container = (PicoContainer) payload;
    //         String sender = msg.getSender();
    //         nodeManager.updateRemoteContainers(sender, container);
    
    //         candidates.remove(container.getName());
    //         return null;
    //     }
    
    //     private Object evaluate_container_request(JMessage msg) {
    //         Object o = msg.getPayload();
    
    //         if (!(o instanceof PicoContainer)) {
    //             logger.error("Message was EVALUATE_CONTAINER_REQUETS but payload was not a container");
    //             return null;
    //         }
    
    //         PicoContainer container = (PicoContainer) o;
    //         String name = container.getName();
    //         double score = nodeManager.getScore();
    //                 //check conflicting ports
    //                 //check confliting names
    //         if (nodeManager.hasContainerName(name)) {
    //             logger.warn("Container with name {} already occupied.", name);
    //             return NAME_CONFLICT;
    //         }
    
    //         String conflictingPort = nodeManager.hasContainerPort(container.getPorts());
    //         if (conflictingPort != null) {
    //             logger.warn("New container with name {} has port conflict: {} is already used", name, conflictingPort);
    //             return PORT_CONFLICT;
    //         }
    
    //         logger.info("Score evaluted to: {}", score);
    //         // return score;
            
    //         JMessage reply = new JMessage()
    //             .setPayload(score)
    //             .setSender(nodeManager.getChannelAddress())
    //             .setType(MessageType.EMPTY);
    
    //         return reply;
    //     }
    
    //     private PicoContainer container_election_start(JMessage msg) {
    //         Object o = msg.getPayload();
    //         if (!(o instanceof PicoContainer)) {
    //             logger.error("Received CONTAINER_ELECTION_START but payload was not a container!");
    //             return null;
    //         }
    
    //         PicoContainer container = (PicoContainer) o;
    //         JMessage newMsg = new JMessage()
    //             .setSender(nodeManager.getChannelAddress())
    //             .setPayload(container)
    //             .setType(MessageType.EVALUATE_CONTAINER_REQUEST);
            
    
    //         try {
    //             Future<List<JMessage>> future = controller.pool.submit(() -> {
    //                 return broadcast(newMsg).stream()
    //                     .map(obj -> (JMessage) obj)
    //                     .toList();
    //             });
                
    //             try {
    //                 List<JMessage> replies = future.get();
    
    //                 double minScore = Double.MAX_VALUE;
    //                 String minAddr = null;
    //                 for (JMessage reply : replies) {
    //                     double score = (Double) reply.getPayload();
    //                     logger.info("Received reply from {} with score {}", reply.getSender(), score);
    //                     if (score < minScore) {
    //                         minScore = score;
    //                         minAddr = reply.getSender();
    //                     }
    //                 }
        
    //                 String name = container.getName();
    //                 if (minScore == NAME_CONFLICT ) {
    //                     container.setState(PicoContainerState.NAME_CONFLICT);
    //                     logger.warn("Container {} has name conflict, it will not be started!", name);
    //                 }
    //                 else if (minScore == PORT_CONFLICT || candidates.containsKey(name)) {
    //                     container.setState(PicoContainerState.PORT_CONFLICT);
    //                     logger.warn("Container {} has port conflicts, it will not be started!", name);
    //                 }
        
    //                 JMessage reply = new JMessage()
    //                     .setSender(nodeManager.getChannelAddress())
    //                     .setPayload(container)
    //                     .setType(MessageType.CREATE_CONTAINER);
        
    //                 //mark container as candidate
    //                 candidates.put(name, container);
        
    //                 logger.info("Container election finished for {} finished, sending result to {}", name, minAddr);
    //                 send(nodeManager.getAddressOfNode(minAddr), reply);
        
    //                 return container;
    //             } catch (Exception e) {
    //                 logger.error("Failed to get replies from nodes: {}", e.getMessage());
    //                 return null;
    //             }
        
    //         } catch (Exception e) {
    //             logger.error("Could not broadcast message: {}", e.getMessage());
    //             return null;
    //         }
    //     }
    }
}
