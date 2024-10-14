package se.umu.cs.ads.types;

import java.io.Serializable;
import se.umu.cs.ads.types.PicoAddress;
import java.util.ArrayList;
import java.util.List;

public class Node implements Serializable {
	private static final long serialVersionUID = 69691337L;
    // private String name;
    private PicoAddress address;
    private String cluster;
    // private int port;

    private final ArrayList<PicoContainer> containers;
    

    public Node() {
        this.containers = new ArrayList<>();
    }

    public Node(PicoAddress address, String cluster, ArrayList<PicoContainer> containers) {
        this.address = address;
        this.cluster = cluster;
        this.containers = containers;
    }

    // public String getName() {
    //     return name;
    // }

    // public void setName(String name) {
    //     this.name = name;
    // }

    public PicoAddress getAddress() {
        return address;
    }


    // public int getPort() {
    //     return port;
    // }

    // public void setPort(int port) {
    //     this.port = port;
    // }

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
		return String.format("%s", address);
	}

	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}
}
