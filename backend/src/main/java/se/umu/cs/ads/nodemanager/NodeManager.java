package se.umu.cs.ads.nodemanager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.controller.Controller;
import se.umu.cs.ads.metrics.SystemMetric;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.messagehandler.MessageHandler;
import se.umu.cs.ads.clustermanager.ClusterManager;

/**
 * Class for cluster management
 */
public class NodeManager {
	private final static Logger logger = LogManager.getLogger(NodeManager.class);
	private final Controller controller;
	private final MessageHandler handler;
	private final SystemMetric metrics;
	private final ClusterManager cluster;
    public final Node node;
	
	//name, containers
	private final Map<String, List<PicoContainer>> remoteContainers;

    public NodeManager(Controller controller, String cluster) {
		String ip = InetAddress.getLocalHost().getHostAddress();
		int port = 8081;

        this.node = new Node(cluster);
		this.node.setAddress(ip);
		this.controller = controller;
		this.remoteContainers = new ConcurrentHashMap<>();
		this.metrics = new SystemMetric();
		this.handler = new MessageHandler(this);
		this.cluster = new ClusterManager(ip, port);
    }


    // Node information management -------------------------------------------------

    public Node getNode() {
        return this.node;
    }

    public Node getNode(InetSocketAddress ipPort) throws RuntimeException {
		String ip = ipPort.getAddress().getHostAddress();
		int port = ipPort.getPort();

        if (ip.equals(this.node.getAddress()) && port == this.node.getPort()) {
            return this.node;
        } else {
            JMessage msg = new JMessage(
                MessageType.FETCH_NODE,
                ip + ":" + port
            );

            JMessage res = send(ip, port, msg);
			Object payload = res.getPayload();
			if (!(payload instanceof Node))
				throw new RuntimeException("Invalid response from FETCH_NODE. Not of type Node");

            return (Node) payload;
        }
    }

    public List<Node> getNodes() throws Exception {
        JMessage msg = new JMessage(
            MessageType.FETCH_NODES,
            ""
        );

        return broadcast(msg).stream()
            .map(obj -> (Node) obj.getPayload())
            .toList();
    }

	public Performance getNodePerformance(InetSocketAddress ipPort) throws Exception {
		String ip = ipPort.getAddress().getHostAddress();
		int port = ipPort.getPort();
		
		JMessage msg = new JMessage(
			MessageType.FETCH_NODE_PERFORMANCE,
			ip + ":" + port
		);

		JMessage res = send(ip, port, msg);
		Object payload = res.getPayload();
		if (!(payload instanceof Performance))
			throw new RuntimeException("Invalid response from FETCH_NODE_PERFORMANCE. Not of type Performance");
		
		return (Performance) payload;
	}

	public synchronized void setActiveContainers(List<PicoContainer> containers) {
		this.node.setContainers(containers);
	}

    // Cluster and channel management ----------------------------------------------
    
	// public boolean isLeader() {
	// 	return getLeader().toString().equals(getChannelAddress());
	// }

	// public Address getLeader() {
    //     return this.view.get().getMembers().get(0);
    // }

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
     * Broadcast a message over the cluster
     * @throws Exception exception
     */
    public List<JMessage> broadcast(JMessage msg) throws Exception {
		// IP/Port should be added somewhere lower
		return this.cluster.broadcast(msg);
    }

    /**
     * Send a message to a specific node
     */
    public JMessage send(String ip, int port, JMessage msg) throws Exception {
        return this.cluster.send(ip, port, msg);
    }

	public ExecutorService getPool() {
		return this.controller.getPool();
	}

	public PicoContainer createLocalContainer(PicoContainer container) {
		return this.controller.createContainer(container);
	}

	public PicoContainer startContainer(String name) {
		return this.controller.startContainer(name);
	}

	public void receive(JMessage message) {
		//handle message here
	}
}