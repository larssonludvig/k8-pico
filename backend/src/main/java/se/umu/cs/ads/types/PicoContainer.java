package se.umu.cs.ads.types;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;

import se.umu.cs.ads.serializers.ContainerDeserializer;
import se.umu.cs.ads.serializers.ContainerSerializer;
import se.umu.cs.ads.utils.Util;

import java.io.Serializable;
import java.util.*;

@JsonSerialize(using = ContainerSerializer.class)
@JsonDeserialize(using = ContainerDeserializer.class)
public class PicoContainer implements Serializable {
	private static final long serialVersionUID = 13376969L;

    private final String id;
    private String name;
    private String image;
    private Map<Integer, Integer> ports = new HashMap<>();
	private List<String> env = new ArrayList<>(); 
    private PicoContainerState state;

    public String getName() {
        return name;
    }

    public PicoContainer setName(String name) {
        this.name = name;
        return this;
    }

    public String getImage() {
        return image;
    }

    public PicoContainer setImage(String image) {
        this.image = image;
        return this;
    }

    public PicoContainer setPorts(Map<Integer, Integer> ports) {
		this.ports = ports;
        return this;
    }

	public PicoContainer setEnv(List<String> env) {
		this.env = env;
		return this;
	}

    public PicoContainerState getState() {
        return state;
    }

	public PicoContainer setState(PicoContainerState state) {
		this.state = state;
		return this;
	}

	public List<String> getPorts() {
		List<String> formattedPorts = new ArrayList<>();
		for (Integer publicPort : ports.keySet()) {
			Integer internaPort = ports.get(publicPort);
			formattedPorts.add(publicPort.toString() + ":" + internaPort.toString());
		}
		return formattedPorts;
	}

	public List<ExposedPort> getExposedPorts() {
		List<ExposedPort> exposedPorts = new ArrayList<>();
		for (Integer port : this.ports.keySet())
			exposedPorts.add(new ExposedPort(port));
		return exposedPorts;
	}

    public String getId() {
        return id;
    }

	public List<String> getEnv() {
		return env;
	}

	public PicoContainer() {
		this.id = null;
	}

    public PicoContainer(String id) {
        this.id = id;
    }

    public PicoContainer(Container container) {
        this.id = container.getId();
        this.name = Util.parseContainerName(container.getNames()[0]);
        this.image = container.getImage();		

		Map<Integer, Integer> ports = Util.containerPortsToInt(container.getPorts());
		setPorts(ports);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PicoContainer) {
            PicoContainer container = (PicoContainer) obj;
            return container.getName().equals(this.name);
        }
        return false;
    }

	@Override
	public String toString() {
		return String.format("{} {}", this.name, this.image);
	}

}
