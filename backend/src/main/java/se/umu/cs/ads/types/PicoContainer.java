package se.umu.cs.ads.types;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.ContainerSerializer;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import se.umu.cs.ads.serializers.*;
import se.umu.cs.ads.utils.*;


@JsonSerialize(using = ContainerSerializer.class)
@JsonDeserialize(using = ContainerDeserializer.class)
public class PicoContainer implements Serializable {
	private static final long serialVersionUID = 13376969L;

    private String name;
    private String image;
    private Map<Integer, Integer> ports = new HashMap<>();
	private List<String> env = new ArrayList<>(); 
    private PicoContainerState state;

	public PicoContainer() {
	}

	public PicoContainer(String name) {
		this.name = name;
	}

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

	public Map<Integer, Integer> getPortsMap() {
		return ports;
	}

	public List<ExposedPort> getExposedPorts() {
		List<ExposedPort> exposedPorts = new ArrayList<>();
		for (Integer port : this.ports.keySet())
			exposedPorts.add(new ExposedPort(port));
		return exposedPorts;
	}



	public List<String> getEnv() {
		return env;
	}


    public PicoContainer(Container container) {
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
