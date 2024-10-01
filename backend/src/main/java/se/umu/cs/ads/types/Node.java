package se.umu.cs.ads.types;

import java.util.ArrayList;
import java.io.Serializable;

public class Node implements Serializable {
    private String name;
    private String address;
    private int port;
    private String cluster;

    private ArrayList<Pod> pods = new ArrayList<>();

    public Node() {}

    public Node(String name) {
        this.name = name;
    }

    public Node(String name, String address, int port, String cluster) {
        this.name = name;
        this.address = address;
        this.port = port;
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

    public void setAddress(String address) {
        this.address = address;
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

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public ArrayList<Pod> getPods() {
        return pods;
    }

    public void setPods(Pod pod) {
        this.pods.add(pod);
    }

    public void removePod(Pod pod) {
        this.pods.remove(pod);
    }
}
