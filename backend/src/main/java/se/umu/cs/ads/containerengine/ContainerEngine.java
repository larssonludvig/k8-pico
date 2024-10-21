package se.umu.cs.ads.containerengine;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import io.grpc.Status.Code;

import com.github.dockerjava.api.exception.*;

import org.json.*;

import java.time.Duration;
import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.utils.Util;
public class ContainerEngine {
    private final DockerClient client;
    private final HostConfig hostConfig;

    private final Set<String> pulledImages;

    //container name, container
    private final Map<String, PicoContainer> containers;
	private final Map<String, String> containerIDs;

    private final static Logger logger = LogManager.getLogger(ContainerEngine.class.getName());

    public ContainerEngine() {

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        client = DockerClientImpl.getInstance(config, httpClient);
        containers = new ConcurrentHashMap<>(64);
		containerIDs = new ConcurrentHashMap<>(64);
        pulledImages = ConcurrentHashMap.newKeySet();
        hostConfig = configureHost();
       
		setImages(readImages());
		setContainers(readContainers(true));
    }



    /**
     * Returns a list of all names currently used by the ContainerEngine
     * @return
     */
    public List<String> getContainerNames() {
		return containers.values().stream().map(PicoContainer::getName).toList();
    }

	public List<PicoContainer> getContainers(boolean showAll) {
		return new ArrayList<PicoContainer>(readContainers(showAll).values());
	}


    private HostConfig configureHost() {
        HostConfig conf = new HostConfig();
        // conf.withPublishAllPorts(true);
        //Optionally set CPU and memory constraints
        return conf;
    }

    /**
     * Pull a specific image: "image:version"
     * @param imageName its name including version.
     * @throws InterruptedException
     */
    public void pullImage(String imageName) throws PicoException {
        logger.info("Pulling container image {}", imageName);
        try {
            client.pullImageCmd(imageName).start().awaitCompletion();
        } catch (InterruptedException e) {
			String err = String.format("Unable to pull image %s, cause: %s", imageName, e.getMessage());
            throw new PicoException(err, Code.CANCELLED);
        }
    }

	public synchronized void setContainers(Map<String, PicoContainer> containers) {
		this.containers.clear();
		this.containers.putAll(containers);
	}

	public synchronized void setImages(List<String> images) {
		this.pulledImages.clear();
		this.pulledImages.addAll(images);
	}

	public boolean hasContainer(String name) {
		return containers.containsKey(name);
	}

    /**
     * Fetch all available containers from the deamon.
     * Similar ot running $ docker container ls -a
     */
    public Map<String, PicoContainer> readContainers(boolean showAll) {
		Map<String, PicoContainer> containers = new HashMap<>();
        List<Container> containersList = client.listContainersCmd().withShowAll(showAll).exec();
        for (Container cont : containersList) {
            String id = cont.getId();
            String name = Util.parseContainerName(cont.getNames()[0]);
            String image = cont.getImage();
			Map<Integer, Integer> ports = Util.containerPortsToInt(cont.getPorts());

			InspectContainerResponse resp = client.inspectContainerCmd(id).exec();
			List<String> env = parseEnv(resp.getConfig().getEnv());
			PicoContainerState state = parseState(resp.getState());

            logger.info("Found container {} of image {} with id {}", name, image, id);

            PicoContainer container = new PicoContainer(cont).setName(name).setImage(image).setPorts(ports).setEnv(env).setState(state);
            containers.put(name, container);
			containerIDs.put(name, id);
        }
		return containers;
    }

	private PicoContainerState parseState(ContainerState state) {
		if (state.getRunning())
			return PicoContainerState.RUNNING;
		else if (state.getRestarting())
			return PicoContainerState.RESTARTING;
		return PicoContainerState.STOPPED;
	}

    public List<String> readImages() {
        List<Image> images = client.listImagesCmd().withShowAll(true).exec();
		List<String> imageNames = new ArrayList<>();
        for (Image img : images) {
            String[] tags = img.getRepoTags();
            imageNames.addAll(Arrays.asList(tags));
        }
		return imageNames;
    }

	private List<String> parseEnv(String[] fullEnv) {
		List<String> env = new ArrayList<>();
		for (String var : fullEnv) {
			if (var.startsWith("K8"))
				env.add(var);
		}
		return env;
	}

	public PicoContainer getContainer(String name) throws PicoException {
		PicoContainer p = containers.get(name);
		if (p == null)
			throw new PicoException("No container with name " + name, Code.NOT_FOUND);
		return p;
	}

    /**
     * Check if a container is running
     * @param id the id of the container
     * @return true or false
     * @throws PicoException If the operation failed
     */
    public boolean isRunning(String name) throws PicoException {
		PicoContainer p = getContainer(name);
		return p.getState() == PicoContainerState.RUNNING;
    }


	public synchronized PicoContainer createContainer(PicoContainer container) throws PicoException {
		String name = container.getName();
		String image = container.getImage();
    	//Pull the image if it doesn't exist
        if (!pulledImages.contains(image))
            pullImage(image);
        else
            logger.info("Container image {} already pulled since start, skipping.", image);


        if (containers.containsKey(name))
            throw new PicoException("Container with name " + name + " already exists", Code.ALREADY_EXISTS);


        logger.info("Creating container with name {} ...", name);
        CreateContainerResponse resp;
        try {
            HostConfig cfg = new HostConfig();
            cfg.withPortBindings(container.getBindings());
            cfg.withPublishAllPorts(true);
			cfg.withRestartPolicy(RestartPolicy.unlessStoppedRestart());

            /**
             * We are limited to our time. Setting exported ports have been an
             * issue with the library since 2020, be it has not been fixed yet.
             * 
             * See GitHub issue for more info:
             *      https://github.com/docker-java/docker-java/issues/1281
             */
            resp = client.createContainerCmd(image)
                    .withHostConfig(cfg)
                    .withName(name)
                    .withHostName(name)
                    .withEnv(container.getEnv())
                    .exec();
        } catch (DockerException e) {
            String message = parseDockerException(e);
            throw new PicoException(message, Code.INTERNAL);
        } catch (Exception e) {
            String message = e.getMessage();
            throw new PicoException(message);
        }
        String id = resp.getId();
        logger.info("Container {} created with id {}", name, id);
		containers.put(name, container);
		containerIDs.put(name, id);
        //we need to re-read it to know port numbers...
		//TODO: FIX
		Map<String, PicoContainer> conts = readContainers(true);
        try {
            while (!conts.containsKey(name)) {
                Thread.sleep(5);
				conts = readContainers(true);
            }
        } catch (InterruptedException e) {
            throw new PicoException("Interrupted while creating new container", Code.CANCELLED);
		}
		PicoContainer created = conts.get(name);
		containers.put(name, created);
		logger.info("Done creating container {}", name);
		created.setState(PicoContainerState.STOPPED);

		runContainer(name);
        return created;

	}


    /**
     * Create a new container given the image and the container name
     *
     * <p>
     *     This function will take care if the provided image is not found, and pull if required.
     * </p>
     * @param imageName the name of the image, including its version seperated by colon ':'
     * @param containerName the name if the new image
     * @return The newly created container
     * @throws PicoException if the underlying operations failed
     */
    public PicoContainer createContainer(String imageName, String containerName) throws PicoException {
            PicoContainer tmp = new PicoContainer().setImage(imageName).setName(containerName);
			return createContainer(tmp);
        }

    /**
     * Start a container
     *
     * @param name the name of the container to be started.
     * @return
     * @throws PicoException
     */
    public synchronized PicoContainer runContainer(String name) throws PicoException {
		PicoContainer container;
		synchronized (this) {
			if (!containers.containsKey(name)) {
				String err = String.format("No container with name %s was found. Create it first!", name);
				throw new PicoException(err, Code.NOT_FOUND);
			}
			else
				container = containers.get(name);
		}

        String id = containerIDs.get(name);
        logger.info("Starting container {} ...", name);

        if (isRunning(name)) {
            logger.warn("Trying to start a container that is already running. Skipping.");
            return container;
        }

        try {
            client.startContainerCmd(id).exec();
        } catch (DockerException e) {
            String msg = parseDockerException(e);
			String err = String.format("Unable to start container %s, cause: %s", name, msg);
            throw new PicoException(err, Code.INTERNAL);
        }

		container.setState(PicoContainerState.RUNNING);
		logger.info("Done starting container {}", name);
        return container;
    }

    /**
     * Restart a container
     * @param name name of the container to be restarted
     */
    public void restartContainer(String name) throws PicoException {
        PicoContainer container = containers.get(name);
        if (container == null)
            throw new PicoException("Could not restart container. No container with name: " + name, Code.NOT_FOUND);

		String id = containerIDs.get(name);

        try {
            logger.info("Restarting container {} ...", name);
            client.restartContainerCmd(id).exec();
        } catch (DockerException e) {
            String msg = parseDockerException(e);
			String err = String.format("Unable to restart container %s with cause: %s", name, msg);
            throw new PicoException(err, Code.INTERNAL);
        }

		container.setState(PicoContainerState.RESTARTING);
		logger.info("Done restarting container {}", name);
    }

    private String parseDockerException(RuntimeException e) {
        int jsonStart = e.getMessage().indexOf("{");
        if (jsonStart == -1) {
            return e.getLocalizedMessage();
        }

        String json = e.getMessage().substring(jsonStart);
        JSONObject o = new JSONObject(json);
        return o.getString("message");
    }

    public void stopContainer(String name) throws PicoException {
        try {
            String id = containerIDs.get(name);
            logger.info("Stopping container {} with id {} ...", name, id);
            client.stopContainerCmd(id).exec();
        } catch (NotModifiedException e) {
			logger.warn("Trying to stop a already stopped container. Ignoring");
        } catch (DockerException e) {
            String msg = parseDockerException(e);
			String err = String.format("Unable to stop container %s, cause: %s", name, msg);
            throw new PicoException(err, Code.INTERNAL);
		}

		PicoContainer container = containers.get(name);
		container.setState(PicoContainerState.STOPPED);

		logger.info("Done stopping container {}", name);
    }

    public void removeContainer(String name) throws PicoException {
		logger.info("Removing container {} ...", name);
        try {
			synchronized (this) {
				String id = containerIDs.get(name);
				stopContainer(name);
				client.removeContainerCmd(id).exec();
				containers.remove(name);
				containerIDs.remove(name);
			}
        } catch (DockerException e) {
            String msg = parseDockerException(e);
            throw new PicoException(String.format("Failed to remove container %s, cause: %s", name, msg), Code.INTERNAL);
        }
		logger.info("Done removing container {}", name);
    }

    public List<String> containerLog(String name) throws InterruptedException, IOException {
        logger.info("Fetching logs for container {}", name);
		
		if (!containerIDs.containsKey(name)) {
			logger.warn("No container with name {}", name);
			return new ArrayList<>();
		}
		
		String id = containerIDs.get(name);
        List<String> logs = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
			client.logContainerCmd(id)
                .withTimestamps(true)
                .withStdOut(true)
                .withStdErr(true)
                .exec(new CallLog(logs, latch));
		} catch(NotFoundException e) {
			logger.warn("No container with id {}. Nothing to do.", id);
			return new ArrayList<>();
		}
        latch.await();
		logger.info("Done fetching {} logs for container {}", logs.size(), name);
        return logs;
    }

    private class CallLog implements ResultCallback<Frame> {
        private final List<String> logsList;
        private final List<String> logsWhenComplete;
        private final CountDownLatch latch;
        public CallLog(List<String> logs, CountDownLatch latch) {
            logsList = new ArrayList<>();
            logsWhenComplete = logs;
            this.latch = latch;
        }

        @Override
        public void onStart(Closeable closeable) {}

        @Override
        public void onNext(Frame object) {
            String entry = new String(object.getPayload());
            if (entry.endsWith("\n"))
                entry = entry.substring(0, entry.length() - 1);
           logsList.add(entry);
        }

        @Override
        public void onError(Throwable throwable) {
            latch.countDown(); //TODO: Error handling?
        }

        @Override
        public void onComplete() {
            logsWhenComplete.addAll(logsList);
            latch.countDown();
        }

        @Override
        public void close() {
            latch.countDown();
        }
    }
}
