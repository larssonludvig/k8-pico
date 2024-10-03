package se.umu.cs.ads.types;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

public class Node implements Serializable {
	private static final long serialVersionUID = 69691337L;
    private String name;
    private String address;
    private String cluster;
    private static final int PORT = 8080;

    private final ArrayList<PicoContainer> containers;
    

    public Node(String cluster) {
        this.cluster = cluster;
        this.containers = new ArrayList<>();
    }



    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }


    public int getPort() {
        return PORT;
    }

    public String getCluster() {
        return cluster;
    }

	public void setAddress(String addr) {
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

	@Override
	public String toString() {
		return String.format("%s (%s)", name, address);
	}
}
