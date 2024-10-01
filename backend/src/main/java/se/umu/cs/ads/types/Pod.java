package se.umu.cs.ads.types;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;

import se.umu.cs.ads.serializers.PodDeserializer;
import se.umu.cs.ads.serializers.PodSerializer;
import se.umu.cs.ads.utils.Util;
import java.util.*;

@JsonSerialize(using = PodSerializer.class)
@JsonDeserialize(using = PodDeserializer.class)
public class Pod {
    private final String id;
    private String name;
    private String image;
    private Map<Integer, Integer> ports = new HashMap<>();
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

    public Pod setPorts(Map<Integer, Integer> ports) {
		this.ports = ports;
        return this;
    }

	public Pod setEnv(List<String> env) {
		this.env = env;
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

	public Pod() {
		this.id = null;
	}

    public Pod(String id) {
        this.id = id;
    }

    public Pod(Container container) {
        this.id = container.getId();
        this.name = Util.parsePodName(container.getNames()[0]);
        this.image = container.getImage();		

		Map<Integer, Integer> ports = Util.containerPortsToInt(container.getPorts());
		setPorts(ports);
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
