package se.umu.cs.ads.podengine;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.exec.LogContainerCmdExec;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import com.github.dockerjava.api.exception.*;

import jdk.vm.ci.code.site.Call;
import org.apache.hc.core5.net.Host;
import org.json.*;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PodEngine {
    private final DockerClient client;
    private final Executor pool;
    private final HostConfig hostConfig;
    //image name, container id
    private final HashMap<String, String> containerIDs;

    //container name, container id
    private final HashMap<String, String> containerNames;

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
        containerIDs = new HashMap<>();
        containerNames = new HashMap<>();
        pool = Executors.newCachedThreadPool();

        //Configure host config
        hostConfig = configureHost();

    }

    private HostConfig configureHost() {
        HostConfig conf = new HostConfig();
        conf.withPublishAllPorts(true);
        //Optionally set CPU and memory constraints
        return conf;
    }

    public void pullImage(String imageName) throws InterruptedException {
        logger.info("Pulling container image {}", imageName);
        client.pullImageCmd(imageName).start().awaitCompletion();
    }

    public void refreshContainers() {
        List<Container> containers = client.listContainersCmd().withShowAll(true).exec();

        for (Container cont : containers) {
            String id = cont.getId();
            String name = cont.getNames()[0];
            String image = cont.getImage();
            logger.info("Found container {} of image {} with id {}", name, image, id);

            if (name.startsWith("/"))
                name = name.substring(1);

            containerNames.put(name, id);
            containerIDs.put(image, id);
        }
    }


    public boolean isRunning(String containerId) {
        List<Container> conts = client.listContainersCmd()
                .withIdFilter(Collections.singleton(containerId))
                .exec();

        int size = conts.size();
        if (size > 1)
            throw new IllegalStateException("Searched for container with id " + containerId + " and found " + size + " container. Only one should be possible.");

        return size == 1;
    }
    public String runContainer(String imageName, String containerName) throws Exception {
        
		try {
            if (!containerIDs.containsKey(imageName)) {
                pullImage(imageName);
            } else
                logger.info("Container image {} already pulled since start, skipping.", imageName);


            if (!containerNames.containsKey(containerName)) {
                logger.info("Creating container with name {}", containerName);
                CreateContainerResponse resp = client.createContainerCmd(imageName)
                        .withHostConfig(hostConfig)
                        .withName(containerName)
                        .exec();
                containerNames.put(containerName, resp.getId());
            } else {
                logger.info("Found existing container with name {}", containerName);
            }

            String id = containerNames.get(containerName);
            logger.info(String.format("Container has id: %s", id));
            containerIDs.putIfAbsent(containerName, id);

            if (isRunning(id))
                restartContainer(containerName);
            else
                client.startContainerCmd(id).exec();

            return id;

		} catch (DockerException e) {
            e.printStackTrace();
			throw new Exception(parseDockerException(e));
		}
    }

    public void restartContainer(String containerName) {
        String id = containerNames.get(containerName);
        if (id == null)
            return; //TODO: better error checking

        client.restartContainerCmd(id).exec();
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

    public void stopContainer(String containerName) {
        String id = containerIDs.get(containerName);
        client.stopContainerCmd(id).exec();
    }

    public void removeContainer(String containerName) {
        String id = containerIDs.get(containerName);
        stopContainer(containerName);
        client.removeContainerCmd(id).exec();
    }

    public List<String> containerLog(String containerName) throws InterruptedException, IOException {
        System.out.println("LOLOLOLOLOLOLOL");
        String id = containerIDs.get(containerName);
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

        /**
         * Closes this stream and releases any system resources associated
         * with it. If the stream is already closed then invoking this
         * method has no effect.
         *
         * <p> As noted in {@link AutoCloseable#close()}, cases where the
         * close may fail require careful attention. It is strongly advised
         * to relinquish the underlying resources and to internally
         * <em>mark</em> the {@code Closeable} as closed, prior to throwing
         * the {@code IOException}.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            latch.countDown();
        }
    }
}
