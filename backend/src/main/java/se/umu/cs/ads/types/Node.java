package se.umu.cs.ads.types;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ext.DOMDeserializer.NodeDeserializer;

import java.io.Serializable;
import java.net.InetSocketAddress;

@JsonDeserialize(using = NodeDeserializer.class);
public class Node implements Serializable {
	private static final long serialVersionUID = 69691337L;
    private String name;
    private InetSocketAddress address;
    private String cluster;
    private int port;

    private final ArrayList<PicoContainer> containers;
    

    public Node() {
        this.containers = new ArrayList<>();
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InetSocketAddress getAddress() {
        return address;
    }


    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCluster() {
        return cluster;
    }

	public void setAddress(InetSocketAddress addr) {
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

	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}
}
