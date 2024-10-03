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

    private final ArrayList<Pod> pods;
    

    public Node(String cluster) {
        this.cluster = cluster;
        this.pods = new ArrayList<>();
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

    public ArrayList<Pod> getPods() {
        synchronized (this) {
			//create a copy so no one can modify the original list
			return new ArrayList<>(pods);
		}
    }

    public void setPods(List<Pod> pods) {
        synchronized (this) {
			this.pods.clear();
			this.pods.addAll(pods);
		}
    }

	@Override
	public String toString() {
		return String.format("%s (%s)", name, address);
	}
}
