package se.umu.cs.ads.types;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.ExposedPort;

import java.util.*;

public class Pod {
    private final String id;
    private String name;
    private String image;
    private int[] externalPorts = null;
    private List<ExposedPort> ports = new ArrayList<>();
	private List<String> env = new ArrayList<>(); 

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

    public Pod setPorts(List<Integer> externalPorts) {
		this.ports.clear();
		for (Integer intPort : externalPorts) 
			this.ports.add(new ExposedPort(intPort));
		
        return this;
    }

	public List<ExposedPort> getExposedPorts() {
		ArrayList<ExposedPort> ports = new ArrayList<>();
		for (int port : externalPorts)
			ports.add(new ExposedPort(port));
		return ports;
	}

    public String getId() {
        return id;
    }

	public List<String> getEnv() {
		return env;
	}

	public Pod() {
		this.id = null;
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
		//first count the number of ports we have
		int nrPorts = 0;
		for (int i = 0; i < ports.length; i++) {
			try {
            	this.externalPorts[i] = ports[i].getPublicPort();
				nrPorts++;
			} catch (NullPointerException e) {
				continue;
			}
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Pod) {
            Pod pod = (Pod) obj;
            return pod.getName().equals(this.name);
        }
        return false;
    }
}
