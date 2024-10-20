package se.umu.cs.ads.controller;

import java.util.*;
import java.util.concurrent.*;
import se.umu.cs.ads.types.PicoAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.grpc.Status.Code;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.containerengine.ContainerEngine;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.arguments.*;
import se.umu.cs.ads.clustermanagement.ClusterManager;

public class Controller {
	private final ExecutorService pool;
	private final ContainerEngine engine;
	private final static Logger logger = LogManager.getLogger(Controller.class);
	private final ScheduledExecutorService scheduler;
	private final NodeManager manager;
	private final ClusterManager cluster;
	
	public Controller() {
		pool = CommandLineArguments.pool;
		scheduler = Executors.newScheduledThreadPool(2);
		engine = new ContainerEngine();
		
		manager = new NodeManager(this);
		manager.setActiveContainers(engine.getContainers(true));
		this.cluster = manager.getClusterManager();
		start();
	}

	private void joinCluster() {
		if (CommandLineArguments.initialMember.isBlank()) {
			cluster.createCluster();
			return;
		}
		
		String[] buf = CommandLineArguments.initialMember.split(":");
		String ip = buf[0];
		int port = Integer.parseInt(buf[1]);
		
		PicoAddress initialMember = new PicoAddress(ip, port);
		cluster.joinCluster(initialMember);
	} 

	// Pod engine methods ----------------------------------------------------------

	private void startPeriodicRefresh() {
		scheduler.scheduleAtFixedRate(() -> {
			try {
				long start = System.currentTimeMillis();
				Map<String, PicoContainer> containers = engine.readContainers(true);
				List<String> images = engine.readImages();
				engine.setContainers(containers);
				engine.setImages(images);
				long time = System.currentTimeMillis() - start;
				manager.setActiveContainers(new ArrayList<PicoContainer>(containers.values()));
				logger.info("Refreshed containers and images in {} ms", time);
			} catch (Exception e) {
				logger.error("Failed to refresh container: {}", e.getMessage());
			}
		}, 5, 5, TimeUnit.SECONDS);

		// Heartbeat
		scheduler.scheduleAtFixedRate(() -> {
			this.cluster.heartbeat();
		}, 5, 5, TimeUnit.SECONDS);
	}

	public void shutdown() {
		scheduler.shutdown();
		pool.shutdown();
	}

	public void start() {
		startPeriodicRefresh();
		joinCluster();
	}

	public List<PicoContainer> listAllContainers() throws PicoException {
		Future<List<PicoContainer>> res = pool.submit(() -> {
			return cluster.getAllContainers();
			});

		try {
			return res.get();
		} catch (CancellationException | InterruptedException e) {
			logger.error("Error while fetching containers: " + e.getMessage());
			throw new PicoException("Error while fetching containers: " + e.getMessage(), Code.CANCELLED);
		} catch (ExecutionException e) {
			logger.error("Error while fetching containers: " + e.getMessage());

			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}


	public PicoContainer getRunningContainer(String name) throws PicoException {
		Future<PicoContainer> res = pool.submit(() ->  {
			return engine.getContainer(name);
		});

		try {
			return res.get();
		} catch (CancellationException | InterruptedException e) {
			String err = String.format("Error while retrieving container: " + e.getMessage());
			logger.error(err);
			throw new PicoException(err, Code.CANCELLED);
		} catch (ExecutionException e) {
			logger.error("Error while fetching container: " + e.getMessage());

			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}

	public PicoContainer getContainer(String name) {
		return cluster.getContainer(name);
	}

	public void createContainer(PicoContainer container) throws PicoException {
		Future<PicoContainer> res = pool.submit(() -> {
			cluster.createContainer(container);
			return container;
		});
		try {
			res.get();
		} catch (CancellationException | InterruptedException e) {
			String err = String.format("Error while creating container: " + e.getMessage());
			logger.error(err);
			throw new PicoException(err, Code.CANCELLED);
		} catch (ExecutionException e) {
			logger.error("Error while creating container: " + e.getMessage());
			
			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}

	public PicoContainer createLocalContainer(PicoContainer container) throws PicoException {
		logger.info("Queuing job for container creation: {}", container.getName());
		Future<PicoContainer> res = pool.submit(() -> {
			return engine.createContainer(container);
		});

		try {
			return res.get();
		} catch (CancellationException | InterruptedException e) {
			String err = String.format("Error while creating local container: " + e.getMessage());
			logger.error(err);
			throw new PicoException(err, Code.CANCELLED);
		} catch (ExecutionException e) {
			logger.error("Error while creating container: " + e.getMessage());
			
			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}

	public void removeContainer(String name) throws PicoException {
		pool.submit(() -> {
				engine.removeContainer(name);
			}
		);
	}

	public List<String> getContainerLogs(String name) throws PicoException {
		Future<List<String>> res = pool.submit(() -> {
			return engine.containerLog(name);
		});

		try {
			return res.get();
		} catch (CancellationException | InterruptedException e) {
			String err = String.format("Error while fetching container logs: " + e.getMessage());
			logger.error(err);
			throw new PicoException(err, Code.CANCELLED);
		} catch (ExecutionException e) {
			logger.error("Error while fetching container logs: " + e.getMessage());
			
			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}

	public PicoContainer startContainer(String name) throws PicoException {
		Future<PicoContainer> res = pool.submit(() -> {
			return engine.runContainer(name);
		});

		try {
			return res.get();
		} catch (CancellationException | InterruptedException e) {
			String err = String.format("Error while starting container: " + e.getMessage());
			logger.error(err);
			throw new PicoException(err, Code.CANCELLED);
		} catch (ExecutionException e) {
			logger.error("Error while starting container: " + e.getMessage());
			
			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}


	public boolean hasContainer(String name) {
		return manager.hasContainerName(name);	
	}

	public void stopContainer(String name) throws PicoException {
		pool.submit(() -> {
			engine.stopContainer(name);
		});
	}

	public void restartContainer(String name) throws PicoException {
		pool.submit(() -> {
			engine.restartContainer(name);
		});
	}


	// Node manager methods --------------------------------------------------------

	public Node getNode() {
		return manager.getNode();
	}

	public Node getNode(PicoAddress address) throws Exception {
		Future<Node> res = pool.submit(() -> {
			return manager.getNode(address);
		});

		try {
			return res.get();
		} catch (CancellationException | InterruptedException e) {
			String err = String.format("Error while fetching node %s: %s", address, e.getMessage());
			logger.error(err);
			throw new PicoException(err, Code.CANCELLED);
		} catch (ExecutionException e) {
			logger.error("Error while fetching node {}: {} ", address, e.getMessage());
			
			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}

	public List<Node> getNodes() throws Exception {
			return manager.getNodes();
	}

	public Performance getNodePerformance(PicoAddress address) throws Exception {
		Future<Performance> res = pool.submit(() -> {
			return manager.getNodePerformance(address);
		});

		try {
			return res.get();
		} catch (CancellationException | InterruptedException e) {
			String err = String.format("Error while fetching performance from node %s: %s", address, e.getMessage());
			logger.error(err);
			throw new PicoException(err, Code.CANCELLED);
		} catch (ExecutionException e) {
			logger.error("Error while fetching node {}: {} ", address, e.getMessage());
			
			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}

	public void removeNode(PicoAddress adr) {
		manager.removeNode(adr);
	}

	public void leaveRemote(PicoAddress adr) throws Exception {
		pool.submit(() -> {
			cluster.leaveRemote(adr);
		});
	}

	public String sendRemoteCommand(String containerName, String command) {
		Future<String> res = pool.submit(() ->  {
			return manager.remoteContainerCommand(containerName, command);
		});

		try {
			return res.get();
		} catch (CancellationException | InterruptedException e) {
			String err = String.format("Error while executing remote command %s for container %s:%s", 
				command, containerName, e.getMessage());
			logger.error(err);
			throw new PicoException(err, Code.CANCELLED);
		} catch (ExecutionException e) {			
			if (e.getCause() instanceof PicoException) {
				throw (PicoException) e.getCause();
			}
			throw new PicoException(e.getMessage());
		}
	}
}
