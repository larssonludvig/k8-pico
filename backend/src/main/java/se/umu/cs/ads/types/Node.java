package se.umu.cs.ads.types;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.*;

/**
 * Class for the Node object
 */
public class Node implements Serializable {
	private static final long serialVersionUID = 69691337L;
    private PicoAddress address;
    private String cluster;

    private final HashSet<PicoContainer> containers;
    
	/**
	 * Empty constructor for the Node object
	 */
    public Node() {
        this.containers = new HashSet<>();
    }

	/**
	 * Constructor for the Node object
	 * @param address PicoAddress object
	 * @param cluster String object
	 * @param containers ArrayList of PicoContainer objects
	 */
    public Node(PicoAddress address, String cluster, ArrayList<PicoContainer> containers) {
        this.address = address;
        this.cluster = cluster;
        this.containers = new HashSet<>(containers);
    }

	/**
	 * Gets the address of the node
	 * @return PicoAddress object
	 */
    public PicoAddress getAddress() {
        return address;
    }

	/**
	 * Gets the cluster of the node
	 * @return String object
	 */
    public String getCluster() {
        return cluster;
    }

	/**
	 * Sets the address of the node
	 * @param addr PicoAddress object
	 */
	public void setAddress(PicoAddress addr) {
		this.address = addr;
	}

	/**
	 * Sets the cluster of the node
	 * @param cluster String object
	 */
    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

	/**
	 * Gets the containers of the node
	 * @return ArrayList of PicoContainer objects
	 */
    public ArrayList<PicoContainer> getContainers() {
        synchronized (this) {
			//create a copy so no one can modify the original list
			return new ArrayList<>(containers);
		}
    }

	/**
	 * Adds a container to the node
	 * @param container PicoContainer object
	 */
	public void addContainer(PicoContainer container) {
		synchronized (this) {
			this.containers.add(container);
		}
	}

	/**
	 * Overrides the containers list of the node
	 * @param containers ArrayList of PicoContainer objects
	 */
    public void setContainers(List<PicoContainer> containers) {
        synchronized (this) {
			this.containers.clear();
			this.containers.addAll(containers);
		}
    }

	/**
	 * Gets the ip of the node
	 * @return String object
	 */
    public String getIP() {
        return address.getIP();
    }

	/**
	 * Gets the port of the node
	 * @return int
	 */
    public int getPort() {
        return address.getPort();
    }

	/**
	 * Get a string representation of the node
	 * @return String object
	 */
	@Override
	public String toString() {
		return address.toString();
	}

	/**
	 * Get the hash code of the node
	 * @return int
	 */
	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}

	/**
	 * Check if two nodes are equal
	 * @param o Object
	 * @return boolean
	 */
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (this == o)
			return true;
		if (!(o instanceof Node))
			return false;

		Node n = (Node) o;
		return this.address.equals(n.getAddress());
	}
}
