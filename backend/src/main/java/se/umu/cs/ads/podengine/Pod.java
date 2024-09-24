package se.umu.cs.ads.podengine;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;

public class Pod {
    private final String id;
    private String name;
    private String image;
    private int[] externalPorts;
    private int[] internalPorts;

    public String getName() {
        return name;
    }

    public Pod setName(String name) {
        this.name = name;
        return this;
    }

    public String getImage() {
        return image;
    }

    public Pod setImage(String image) {
        this.image = image;
        return this;
    }

    public Pod setPorts(int[] external, int[] internal) {
        this.externalPorts = external;
        this.internalPorts = internal;
        return this;
    }

    public String getId() {
        return id;
    }

    public Pod(String id) {
        this.id = id;
    }

    public Pod(Container container) {
        this.id = container.getId();
        this.name = container.getNames()[0];

        if (name.startsWith("/"))
            name = name.substring(1);

        this.image = container.getImage();
        ContainerPort[] ports = container.getPorts();
        this.internalPorts = new int[ports.length];
        this.externalPorts = new int[ports.length];

        for (int i = 0; i < ports.length; i++) {
            this.externalPorts[i] = ports[i].getPublicPort();
            this.internalPorts[i] = ports[i].getPrivatePort();
        }
    }
}
