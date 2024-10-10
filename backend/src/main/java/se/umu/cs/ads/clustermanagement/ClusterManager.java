package se.umu.cs.ads.clustermanager;

import java.util.*;
import java.util.concurrent.*;

import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.PicoCommunication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClusterManager {
	// Flytta node hit från NodeManager?
	private final Map<String, Node> cluster;

	private final PicoCommunication comm;

	private final static Logger logger = LogManager.getLogger(ClusterManager.class);

	public ClusterManager(String ip, int port) {
		this.cluster = new ConcurrentHashMap<>();
		this.comm = new PicoCommunication(ip, port);
	}

	public List<Node> getClusterMembers() {
		return this.cluster.values();
	}

	public List<Node> getNodes() {
		return this.cluster.values();
	}

	public void addNode(Node node) {
		this.cluster.put(node.getName(), node);
	}

	public void removeNode(String name) {
		this.cluster.remove(name);
	}

	public void updateNode(Node node) {
		this.cluster.put(node.getName(), node);
	}

	public Node getNode(String name) {
		return this.cluster.get(name);
	}

	public JMessage send(String ip, int port, JMessage msg) {
		return this.comm.send(ip, port, msg);
	}

	public List<JMessage> broadcast(JMessage msg) {
		List<String> ips = new List<>();
		List<Integer> ports = new List<>();

		for (Node node : getNodes()) {
			ips.add(node.getIp());
			ports.add(node.getPort());
		}

		return this.comm.broadcast(ips, ports, msg);
	}
}