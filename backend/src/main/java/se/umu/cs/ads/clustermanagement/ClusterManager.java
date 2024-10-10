package se.umu.cs.ads.clustermanagement;

import java.util.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.*;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.PicoCommunication;
import se.umu.cs.ads.nodemanager.NodeManager;

public class ClusterManager {
	private final static Logger logger = LogManager.getLogger(ClusterManager.class);
	private final Map<InetSocketAddress, Node> cluster;
	private final PicoCommunication comm;
	private final String address;
	private final NodeManager manager;

	public ClusterManager(NodeManager manager, String ip, int port) {
		this.cluster = new ConcurrentHashMap<>();
		this.comm = new PicoCommunication(this, manager.getAddress());
		this.address = ip;
		this.manager = manager;
	}

	public List<Node> getClusterMembers() {
		return new ArrayList<Node>(cluster.values());
	}

	public List<Node> getNodes() {
		return new ArrayList<Node>(cluster.values());
	}

	public void addNode(Node node) {
		cluster.put(node.getName(), node);
	}

	public void removeNode(String name) {
		cluster.remove(name);
	}

	public void updateNode(Node node) {
		cluster.put(node.getName(), node);
	}

	public Node getNode(InetSocketAddress address) {
		return cluster.get(address);
	}

	public JMessage send(JMessage msg) {
		return comm.send(msg);
	}

	public List<JMessage> broadcast(JMessage msg) {
		List<String> ips = new ArrayList<>();
		List<Integer> ports = new ArrayList<>();
		List<InetSocketAddress> addresses = getNodes().stream().map(it -> it.getAddress()).toList();

		return comm.broadcast(addresses, msg);
	}

	public void receive(JMessage message) {
		manager.receive(message);
	}
}