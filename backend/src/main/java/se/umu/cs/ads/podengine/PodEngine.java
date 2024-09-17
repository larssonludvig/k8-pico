package se.umu.cs.ads.podengine;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import com.github.dockerjava.api.exception.*;

import org.json.*;

import java.time.Duration;
import java.util.HashMap;

public class PodEngine {
    private final DockerClient client;

    private final HashMap<String, String> ids;
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
        ids = new HashMap<>();
    }

    private void log(String msg) {
        System.out.println("[+] " + msg);
    }

    private void log(String[] msgs) {
        StringBuilder res = new StringBuilder();
        for (String msg : msgs) {
            res.append(msg).append(" ");
        }
        System.out.println("[+] " + res);
    }

    public String runContainer(String imageName, String containerName) throws Exception {
        
		try {
            log("Pulling container image " + imageName);
            client.pullImageCmd(imageName).start().awaitCompletion();
            log("Creating container with name" + containerName);
            CreateContainerCmd cmd = client.createContainerCmd(imageName).withName(containerName);
            CreateContainerResponse resp = cmd.exec();
            String containerId = resp.getId();
            log(String.format("Container has id (%s)", containerId));
            ids.put(containerName, containerId);

            client.startContainerCmd(containerId).exec();
            return containerId;

		} catch (DockerException e) {
            int jsonStart = e.getMessage().indexOf("{");
            String json = e.getMessage().substring(jsonStart);
            System.err.println(json);
            JSONObject o = new JSONObject(json);
            String message = o.getString("message");
			throw new Exception(message);
		}
    }

    public void stopContainer(String containerName) {
        String id = ids.get(containerName);
        client.stopContainerCmd(id).exec();
    }

}
