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

import se.umu.cs.ads.controller.Controller;
import se.umu.cs.ads.metrics.SystemMetric;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.*;

/**
 * Class for cluster management
 */
public class NodeManager {
	private final static Logger logger = LogManager.getLogger(NodeManager.class);
	private final Controller controller;
	private PicoCommunication comm;
    public final Node node;
	private final SystemMetric metrics;
	private final String address;
	
	//name, containers
	private final Map<String, List<PicoContainer>> remoteContainers;

    public NodeManager(Controller controller, String cluster) {
        this.node = new Node(cluster);
		this.controller = controller;
		this.view = new AtomicReference<>();
		this.view.set(new View());
		this.remoteContainers = new ConcurrentHashMap<>();
		this.address = Inet4Address.getLocalHost().getHostAddress();
		this.metrics = new SystemMetric();
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

		this.comm = new PicoCommunication(8081);

		this.node.setAddress(getIPAddress());
		logger.info("Node: {}", this.node);
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
     * @throws Exception exception
     */
    public List<JMessage> broadcast(JMessage msg) throws Exception {
		// IP/Port should be added somewhere lower
		return this.comm.broadcast(msg);
    }

    /**
     * Send a message to a specific node
     */
    public JMessage send(String ip, int port, JMessage msg) throws Exception {
        return this.comm.send(ip, port, msg);
    }
}