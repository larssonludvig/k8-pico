package se.umu.cs.ads.podengine;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import com.github.dockerjava.api.exception.*;

import org.json.*;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.Pod;
import se.umu.cs.ads.utils.Util;
public class PodEngine {
    private final DockerClient client;
    private final Executor pool;
    private final HostConfig hostConfig;

    private final Set<String> pulledImages;
    //container name, pod
    private final Map<String, Pod> pods;

    private final static Logger logger = LogManager.getLogger(PodEngine.class.getName());

    public PodEngine() {

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        client = DockerClientImpl.getInstance(config, httpClient);
        pods = new ConcurrentHashMap<>();
        pulledImages = ConcurrentHashMap.newKeySet();
        pool = Executors.newCachedThreadPool();
        hostConfig = configureHost();
        refreshImages();
        refreshContainers();
    }

    /**
     * Returns a list of all names currently used by the podengine
     * @return
     */
    public List<String> getPodNames() {
		return pods.values().stream().map(Pod::getName).toList();
    }

	public List<Pod> getContainers() {
		refreshContainers();
		return new ArrayList<Pod>(pods.values());
	}

    private HostConfig configureHost() {
        HostConfig conf = new HostConfig();
        conf.withPublishAllPorts(true);
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
            throw new PicoException(String.format("Unable to pull image %s, cause: %s", imageName, e.getMessage()));
        }
    }

    /**
     * Fetch all available containers from the deamon.
     * Similar ot running $ docker container ls -a
     */
    public void refreshContainers() {
        List<Container> containers = client.listContainersCmd().withShowAll(true).exec();
        for (Container cont : containers) {
            String id = cont.getId();
            String name = Util.parsePodName(cont.getNames()[0]);
            String image = cont.getImage();
			Map<Integer, Integer> ports = Util.containerPortsToInt(cont.getPorts());

			InspectContainerResponse resp = client.inspectContainerCmd(id).exec();
			List<String> env = parseEnv(resp.getConfig().getEnv());


            logger.info("Found container {} of image {} with id {}", name, image, id);

            Pod pod = new Pod(cont).setName(name).setImage(image).setPorts(ports).setEnv(env);
            pods.put(pod.getName(), pod);
        }
    }

	private List<String> parseEnv(String[] fullEnv) {
		List<String> env = new ArrayList<>();
		for (String var : fullEnv) {
			if (var.startsWith("K8"))
				env.add(var);
		}
		return env;
	}

    public void refreshImages() {
        List<Image> images = client.listImagesCmd().withShowAll(true).exec();
        for (Image img : images) {
            String[] tags = img.getRepoTags();
            this.pulledImages.addAll(Arrays.asList(tags));
        }
    }

    public Container getContainer(String id, boolean showAll) throws PicoException {
        List<Container> conts = client.listContainersCmd().withShowAll(showAll).exec();

        for (Container cont : conts) {
            if (cont.getId().equals(id))
                return cont;
        }
        return null;
    }

	public Pod getContainer(String name) {
		return pods.get(name);
	}

    /**
     * Check if a container is running
     * @param id the id of the container
     * @return true or false
     * @throws PicoException If the operation failed
     */
    public boolean isRunning(String id) throws PicoException {
        return getContainer(id, false) != null;
    }


	public Pod createContainer(Pod container) throws PicoException {
		String name = container.getName();
		String image = container.getImage();
    	//Pull the image if it doesn't exist
        if (!pulledImages.contains(name))
            pullImage(image);
        else
            logger.info("Container image {} already pulled since start, skipping.", image);


        if (pods.containsKey(name))
            return pods.get(name);


        logger.info("Creating container with name {}...", name);
        CreateContainerResponse resp;
        try {
			resp = client.createContainerCmd(image)
                    .withHostConfig(hostConfig)
                    .withName(name)
                    .withHostName(name)
					.withEnv(container.getEnv())
					.withExposedPorts(container.getExposedPorts())
                    .exec();
        } catch (DockerException e) {
            String message = parseDockerException(e);
            throw new PicoException(message);
        }
        String id = resp.getId();
        logger.info("Container {} has id {}", name, id);

        //we need to re-read it to know port numbers...
		//TODO: FIX
        Container cont = getContainer(id, true);
        try {
            while (cont == null) {
                Thread.sleep(5);
                cont = getContainer(id, true);
            }
        } catch (InterruptedException e) {
            throw new PicoException("Interrupted while creating new container");
		}

		Map<Integer, Integer> ports = Util.containerPortsToInt(cont.getPorts());

        Pod pod = new Pod(id).setImage(image).setName(name).setPorts(ports);
        pods.put(pod.getName(), pod);
        return container;

	}


    /**
     * Create a new container given the image and the container name
     *
     * <p>
     *     This function will take care if the provided image is not found, and pull if required.
     * </p>
     * @param imageName the name of the image, including its version seperated by colon ':'
     * @param containerName the name if the new image
     * @return The newly created pod
     * @throws PicoException if the underlying operations failed
     */
    public Pod createContainer(String imageName, String containerName) throws PicoException {
			Pod tmp = new Pod().setImage(imageName).setName(containerName);
			return createContainer(tmp);
        }

    /**
     * Start a container
     *
     * @param name the name of the container to be started.
     * @return
     * @throws PicoException
     */
    public Pod runContainer(String name) throws PicoException {
		Pod pod;
		synchronized (this) {
			if (!pods.containsKey(name))
				throw new PicoException(String.format("No pod with name %s was found. Create it first!", name));
			else
				pod = pods.get(name);
		}

        String id = pod.getId();
        logger.info("Container has id {}", id);

        if (isRunning(id)) {
            logger.warn("Trying to start a container that is already running. Skipping.");
            return pod;
        }

        try {
            client.startContainerCmd(id).exec();
        } catch (DockerException e) {
            String msg = parseDockerException(e);
            throw new PicoException(String.format("Unable to start container %s, cause: %s", name, msg));
        }
        return pod;
    }

    /**
     * Restart a container
     * @param name name of the container to be restarted
     */
    public void restartContainer(String name) throws PicoException {
        String id = pods.get(name).getId();
        if (id == null)
            throw new PicoException("Could not restart container. No container with name: " + name);

        try {
            logger.info("Restarting container {}", name);
            client.restartContainerCmd(id).exec();
        } catch (DockerException e) {
            String msg = parseDockerException(e);
            throw new PicoException(String.format("Unable to restart container %s with cause: %s", name, msg));
        }
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

    public void stopContainer(String containerName) throws PicoException {
        try {
            String id = pods.get(containerName).getId();
            logger.info("Stopping container {} with id {}", containerName, id);
            client.stopContainerCmd(id).exec();
        } catch (DockerException e) {
            String msg = parseDockerException(e);
            throw new PicoException(String.format("Unable to stop container %s, cause: %s", containerName, msg));
        }
    }

    public void removeContainer(String containerName) throws PicoException {
        try {
			synchronized (this) {
				String id = pods.get(containerName).getId();
				stopContainer(containerName);
				client.removeContainerCmd(id).exec();
				pods.remove(containerName);
			}
        } catch (DockerException e) {
            String msg = parseDockerException(e);
            throw new PicoException(String.format("Failed to remove container %s, cause: %s", containerName, msg));
        }
    }

    public List<String> containerLog(String containerName) throws InterruptedException, IOException {
        String id = pods.get(containerName).getId();
        List<String> logs = new ArrayList<>();

        CountDownLatch latch = new CountDownLatch(1);
        client.logContainerCmd(id)
                .withTimestamps(true)
                .withStdOut(true)
                .withStdErr(true)
                .exec(new CallLog(logs, latch));

        latch.await();
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
