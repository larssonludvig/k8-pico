package se.umu.cs.ads.nodemanager;

import java.util.List;

import org.jgroups.Message;
import org.jgroups.Address;
import org.jgroups.BytesMessage;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.MessageDispatcher;

import se.umu.cs.ads.types.Pod;
import se.umu.cs.ads.types.JMessage;
import se.umu.cs.ads.podengine.PodEngine;

public class NodeDispatcher implements RequestHandler {
    private MessageDispatcher disp;
    private NodeManager nodeManager;

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
                    PodEngine engine = new PodEngine();
                    Pod pod = (Pod) jmsg.getPayload();

                    try {
                        pod = engine.createContainer(pod.getImage(), pod.getName());
                        pod = engine.runContainer(pod.getName());
                        return "Container started sucessfully!";
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return "Failed to create container.";

                case EMPTY:
                    return "EMPTY, not implemented.";

                default:  
                    return "Unknown message type: " + jmsg.getType() + ".";
            }
        }
        return "Unknown object type.";
    }
}