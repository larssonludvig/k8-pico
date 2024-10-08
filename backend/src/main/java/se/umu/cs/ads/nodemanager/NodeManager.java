package se.umu.cs.ads.nodemanager;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.PhysicalAddress;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.MessageDispatcher;

import se.umu.cs.ads.controller.Controller;
import se.umu.cs.ads.metrics.SystemMetric;
import se.umu.cs.ads.types.JMessage;
import se.umu.cs.ads.types.MessageType;
import se.umu.cs.ads.types.Node;
import se.umu.cs.ads.types.Performance;
import se.umu.cs.ads.types.PicoContainer;

/**
 * Class for cluster management
 */
public class NodeManager {
    private NodeDispatcher disp = null;
    private JChannel ch = null;
    public final Node node;
	private final Controller controller;
	private AtomicReference<View> view;
	private final SystemMetric metrics = new SystemMetric();

	//name, containers
	private final Map<String, List<PicoContainer>> remoteContainers;

	private final static Logger logger = LogManager.getLogger(NodeManager.class);
    public NodeManager(Controller controller, String cluster) {
        this.node = new Node(cluster);
		this.controller = controller;
		this.view = new AtomicReference<>();
		this.view.set(new View());
		this.remoteContainers = new ConcurrentHashMap<>();
    }


    // Node information management -------------------------------------------------

    public Node getNode() {
        return this.node;
    }

	public synchronized void refreshView() {
			View currentView = this.view.get();
			View newView = this.ch.getView();
			List<Address> newMembers = View.newMembers(currentView, newView);
			List<Address> deadMembers = View.leftMembers(currentView, newView);
			
			if (newMembers.size() > 0) {
				System.out.println("New members: " + newMembers.get(0).toString());
				//send our container to new members
				sendContainersTo(newMembers);
			}

			if (deadMembers.size() > 0) {
				System.out.println("Dead members: " + deadMembers.get(0).toString());
			}
			
			this.view.set(newView);
			logger.debug("Current leader: " + getLeader());

	}

	private void sendContainersTo(List<Address> addresses) {
		List<PicoContainer> containers = controller.listAllContainers();
		JMessage msg = new JMessage();

		msg.setSender(this.node.getName());
		msg.setPayload(containers);
		msg.setType(MessageType.CONTAINER_LIST);

		addresses.stream()
		.filter(it -> !it.toString().equals(getChannelAddress())) //filter ourselves out
		.forEach(it -> {
			try {
				logger.info("Sending info regarding {} containers to {}", containers.size(), it);
				send(it, msg);
			} catch (Exception e) {
				logger.error("Could not send message to {}: {}", it, e.getMessage());
			}
		});
	}

    public Node getNode(String nodeName) throws Exception {
        if (nodeName.equals(this.node.getName())) {
            return this.node;
        } else {

            Address address = getAddressOfNode(nodeName);
            if (address == null) {
                throw new Exception("Unable to fetch node, not a member of the cluster.");
            }

            JMessage msg = new JMessage(
                MessageType.FETCH_NODE,
                nodeName
            );

            Object result = send(address, msg);
            if (!(result instanceof Node)) {
                throw new Exception("Fetched object is not of type Node.");
            }

            return (Node) result;
        }
    }

    public List<Node> getNodes() throws Exception {
        JMessage msg = new JMessage(
            MessageType.FETCH_NODES,
            ""
        );

        return broadcast(msg).stream()
            .map(obj -> (Node) obj)
            .toList();
    }

	public Performance getNodePerformance(String nodeName) throws Exception {
		JMessage msg = new JMessage(
			MessageType.FETCH_NODE_PERFORMANCE,
			""
		);

		Address dest = getAddressOfNode(nodeName);
		return (Performance) send(dest, msg);
	}

	public synchronized void setActiveContainers(List<PicoContainer> containers) {
		this.node.setContainers(containers);
	}

    // Cluster and channel management ----------------------------------------------
    
	public boolean isLeader() {
		return getLeader().toString().equals(getChannelAddress());
	}

	public Address getLeader() {
        return this.view.get().getMembers().get(0);
    }

	/**
	 * Add a collection of containers to the list of known host for a remote 
	 * @param name name of the remote host
	 * @param containers containers to add
	 */
    public void updateRemoteContainers(String name, List<PicoContainer> containers) {
		List<PicoContainer> existing = remoteContainers.get(name);
		if (existing == null) {
			existing = new ArrayList<>();
		}

		existing.addAll(containers);
		remoteContainers.put(name, existing);
    }

	/**
	 * Add a container to the list of known remote containers for the given host
	 * @param name name of the host
	 * @param container container to add
	 */
	public void updateRemoteContainers(String name, PicoContainer container) {
		List<PicoContainer> existingContainers = remoteContainers.get(name);
		if (existingContainers == null) {
			existingContainers = new ArrayList<>();

		}

		existingContainers.add(container);
		remoteContainers.put(name, existingContainers);
	}

	/**
	 * Returns a copy of the provided hosts containers
	 * @param name the name of the host
	 * @return list of all container
	 */
	public List<PicoContainer> getRemoteContainers(String name) {
		//We create a copy so the original list can't be modified
		List<PicoContainer> existing = remoteContainers.get(name);
		List<PicoContainer> copy = new ArrayList<>();
		if (existing == null)
			existing = new ArrayList<>();

		copy.addAll(existing);
		return copy;
	}

	public double getCPULoad() {
		return metrics.getCPULoad();
	}

	public double getMemLoad() {
		return metrics.getMemoryLoad();
	}

	public double getFreeMem() {
		return metrics.getFreeMemory();
	}

	public boolean hasContainerName(String name) {
		List<PicoContainer> conts = node.getContainers();
		int hasName = (int) conts.stream().filter(it -> it.getName().equals(name)).count();
		return hasName > 0;
	}

    public String hasContainerPort(List<String> ports) {
		//"8080:80"
		//"8080:443"
        for (PicoContainer cont : node.getContainers()) {
            for (String port : ports) {
                String extPort = port.split(":")[0];
                List<String> knownPorts = cont.getPorts();
                if (knownPorts.stream().filter(p -> p.split(":")[0].equals(extPort)).count() > 0) {
                    return extPort;
                }
            }
        }
        return null;
    }


    /**
     * High score indicates high load
     */
	public double getScore() {
        double w_cpu = 1;
        double w_mem = 1;

        double cpuFree = 1 - getCPULoad();
        double memFree = 1 - getMemLoad();

        // 0-2 with low load. 1-3 with meduim load. 2-4 with high load
		if (cpuFree < 0.2) {
            w_cpu = 1 + getCPULoad();
			w_cpu *= 2;
        } 
        if (memFree < 0.2) {
			w_mem = 1 + getMemLoad();
            w_mem *= 2;
        }

        return (w_cpu * cpuFree) + (w_mem * memFree);
	}


    /**
     * Create or join cluster by paramiters
     * @param cluster Name of cluster to join
     * @param node Name of this node
     * @throws Exception IllegalArgumentException
     */
    public void start() throws Exception {
       

		this.node.setName(InetAddress.getLocalHost().getHostName());

		String cluster = this.node.getCluster();
        this.ch = new JChannel("udp.xml")
            .name(node.getName())
            // .setDiscardOwnMessages(true)
            .setReceiver(new CustomReceiver(node.getName(), view));

        NodeDispatcher nDisp = new NodeDispatcher();
        this.disp = nDisp.initialize(
            new MessageDispatcher(this.ch, nDisp),
            this,
			controller
        );

        this.ch.connect(cluster);
		this.view.set(ch.getView());
		this.node.setAddress(getIPAddress());
		logger.info("Node: {}", this.node);
	}

	public String getChannelAddress() {
		return this.ch.address().toString();
	}

	public String getIPAddress() {
		if (this.ch == null)
			throw new IllegalStateException("Cannot determine address if channel is not created yet.");

			Object o = this.ch.down(new Event(Event.GET_PHYSICAL_ADDRESS, this.ch.getAddress()));

			if (!(o instanceof PhysicalAddress))
				throw new IllegalStateException("Cannot determine local address");
			
			PhysicalAddress address = (PhysicalAddress) o;
			return address.printIpAddress();
	}

	public void stop() {
		this.ch.disconnect();
	}

    /**
     * Finds the address of a node by name
     * @param node Name of node to get address from
     * @return Found address of node
     */
    public Address getAddressOfNode(String node)  {
        Optional<Address> optDest = ch.view().getMembers().stream()
            .filter(address -> node.equals(address.toString()))
            .findAny();
		
        return optDest.orElse(null);
    }

    /**
     * Broadcast a message over the cluster
     * @param obj Object to broadcast
     * @throws Exception exception
     */
    public List<Object> broadcast(Object obj) throws Exception {
        return this.disp.broadcast(obj);
    }

    /**
     * Send a message to a specific node
     * @param dest Node to send to
     * @param obj Object to send
     * @throws Exception exception
     */
    public Object send(Address dest, Object obj) throws Exception {
        return this.disp.send(dest, obj);
    }

    /**
     * Custom receiver class that implements the JGroups Receiver
     */
    protected static class CustomReceiver implements Receiver {
        protected final String name;
		protected final AtomicReference<View> viewUpdater;
		private static final Logger logger = LogManager.getLogger(CustomReceiver.class);
        /**
         * Custom receiver constructor
         * @param name Name of current node
         */
        protected CustomReceiver(String name, AtomicReference<View> viewUpdater) {
            this.name = name;
			this.viewUpdater = viewUpdater;
        }

        /**
         * Override viewAccepted of Receiver
         * @param v Current cluster views
         */
        // @Override
        public void viewAccepted(View v) {
			viewUpdater.set(v);
		    logger.info("-- [%s] new view: %s\n", name, v);
			logger.error("New leader: " + v.getMembers().get(0));
        }
    }
}