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
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports.Binding;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;

import se.umu.cs.ads.serializers.*;
import se.umu.cs.ads.utils.*;

/**
 * Class for the PicoContainer object
 */
@JsonSerialize(using = PicoContainerSerializer.class)
@JsonDeserialize(using = PicoContainerDeserializer.class)
public class PicoContainer implements Serializable {
	private static final long serialVersionUID = 13376969L;

    private String name;
    private String image;
    private Map<Integer, Integer> ports = new HashMap<>();
	private List<String> env = new ArrayList<>(); 
    private PicoContainerState state;

	/**
	 * Empty constructor for the PicoContainer object
	 */
	public PicoContainer() {}

	/**
	 * Constructor for the PicoContainer object
	 * @param name String object
	 */
	public PicoContainer(String name) {
		this.name = name;
	}

	/**
	 * Gets the name of the container
	 * @return String object
	 */
    public String getName() {
        return name;
    }

	/**
	 * Sets the name of the container
	 * @param name String object
	 */
    public PicoContainer setName(String name) {
        this.name = name;
        return this;
    }

	/**
	 * Gets the image of the container
	 * @return String object
	 */
    public String getImage() {
        return image;
    }

	/**
	 * Sets the image of the container
	 * @param image String object
	 */
    public PicoContainer setImage(String image) {
        this.image = image;
        return this;
    }

	/**
	 * Sets the ports of the container
	 * @param ports Map object
	 * @return PicoContainer object
	 */
    public PicoContainer setPorts(Map<Integer, Integer> ports) {
		this.ports = ports;
        return this;
    }

	/**
	 * Set the environment variables of the container
	 * @param env List of environment variables
	 * @return PicoContainer object
	 */
	public PicoContainer setEnv(List<String> env) {
		this.env = env;
		return this;
	}

	/**
	 * Gets the state of the container
	 * @return PicoContainerState object
	 */
    public PicoContainerState getState() {
        return state;
    }

	/**
	 * Sets the state of the container
	 * @param state PicoContainerState object
	 * @return PicoContainer object
	 */
	public PicoContainer setState(PicoContainerState state) {
		this.state = state;
		return this;
	}

	/**
	 * Gets the ports of the container
	 * @return List of ports
	 */
	public List<String> getPorts() {
		List<String> formattedPorts = new ArrayList<>();
		for (Integer publicPort : ports.keySet()) {
			Integer internaPort = ports.get(publicPort);
			formattedPorts.add(publicPort.toString() + ":" + internaPort.toString());
		}
		return formattedPorts;
	}

	/**
	 * Gets the ports of the container
	 * @return Map of ports
	 */
	public Map<Integer, Integer> getPortsMap() {
		return ports;
	}

	/**
	 * Gets the exposed ports of the container
	 * @return List of ExposedPort objects
	 */
	public List<ExposedPort> getExposedPorts() {
		List<ExposedPort> exposedPorts = new ArrayList<>();
		for (Integer port : this.ports.keySet())
			exposedPorts.add(new ExposedPort(port));
		return exposedPorts;
	}

	/**
	 * Gets the port bindings of the container
	 * @return List of PortBinding objects
	 */
	public List<PortBinding> getBindings() {
        ArrayList<PortBinding> bindings = new ArrayList<>();
        for (int publicPort : ports.keySet()) {
            int internalPort = ports.get(publicPort);
            Binding b = new Binding("0.0.0.0", String.valueOf(internalPort));
            ExposedPort p = new ExposedPort(publicPort);
            PortBinding binding = new PortBinding(b, p);
            bindings.add(binding);
        }
        return bindings;
    }

	/**
	 * Gets the environment variables of the container
	 * @return List of environment variables
	 */
	public List<String> getEnv() {
		return env;
	}

	/**
	 * Constructor for the PicoContainer object
	 * @param container Container object
	 */
    public PicoContainer(Container container) {
        this.name = Util.parseContainerName(container.getNames()[0]);
        this.image = container.getImage();		

		Map<Integer, Integer> ports = Util.containerPortsToInt(container.getPorts());
		setPorts(ports);
    }

	/**
	 * Method to compare two PicoContainer objects
	 * @param obj Object
	 * @return boolean object
	 */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PicoContainer) {
            PicoContainer container = (PicoContainer) obj;
            return container.getName().equals(this.name);
        }
        return false;
    }

	/**
	 * Method to get the hash code of the PicoContainer object
	 * @return int object
	 */
	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}

	/**
	 * Method to get the string representation of the PicoContainer object
	 * @return String object
	 */
	@Override
	public String toString() {
		return String.format("{} {}", this.name, this.image);
	}
}
