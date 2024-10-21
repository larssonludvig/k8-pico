package se.umu.cs.ads.types;

import java.io.Serializable;
import se.umu.cs.ads.types.PicoAddress;
import java.util.ArrayList;
import java.util.*;

public class Node implements Serializable {
	private static final long serialVersionUID = 69691337L;
    private PicoAddress address;
    private String cluster;

    private final HashSet<PicoContainer> containers;
    

    public Node() {
        this.containers = new HashSet<>();
    }

    public Node(PicoAddress address, String cluster, ArrayList<PicoContainer> containers) {
        this.address = address;
        this.cluster = cluster;
        this.containers = new HashSet<>(containers);
    }

    public PicoAddress getAddress() {
        return address;
    }


    public String getCluster() {
        return cluster;
    }

	public void setAddress(PicoAddress addr) {
		this.address = addr;
	}

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public ArrayList<PicoContainer> getContainers() {
        synchronized (this) {
			//create a copy so no one can modify the original list
			return new ArrayList<>(containers);
		}
    }

	public void addContainer(PicoContainer container) {
		synchronized (this) {
			this.containers.add(container);
		}
	}

    public void setContainers(List<PicoContainer> containers) {
        synchronized (this) {
			this.containers.clear();
			this.containers.addAll(containers);
		}
    }

    public String getIP() {
        return address.getIP();
    }

    public int getPort() {
        return address.getPort();
    }

	@Override
	public String toString() {
		return address.toString();
	}

	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}

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
