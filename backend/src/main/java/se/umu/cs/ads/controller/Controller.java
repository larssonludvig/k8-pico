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

/**
 * Controller class is the main class of the backend. It is responsible for
 * managing the container engine, the node manager and the cluster manager.
 */
public class Controller {
	private final ExecutorService pool;
	private final ContainerEngine engine;
	private final static Logger logger = LogManager.getLogger(Controller.class);
	private final ScheduledExecutorService scheduler;
	private final NodeManager manager;
	private final ClusterManager cluster;
	
	/**
	 * Constructor for the Controller class. It initializes the container engine,
	 * the node manager and the cluster manager. It also starts the periodic refresh
	 * of the containers and images and joins the cluster if the initial member is
	 * provided.
	 */
	public Controller() {
		pool = CommandLineArguments.pool;
		scheduler = Executors.newScheduledThreadPool(2);
		engine = new ContainerEngine();
		
		manager = new NodeManager(this);
		manager.setActiveContainers(engine.getContainers(true));
		this.cluster = manager.getClusterManager();
		start();
	}

	/**
	 * Method to join the cluster. If the initial member is not provided, it creates
	 * a new cluster. Otherwise, it joins the cluster with the provided initial member.
	 */
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

	/**
	 * Method to start the periodic refresh of the containers and images. It refreshes
	 * the containers and images every 5 seconds. It also sends a heartbeat to the cluster
	 * every 5 seconds.
	 */
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

	/**
	 * Method to shutdown the controller. It shuts down the scheduler and the pool.
	 */
	public void shutdown() {
		scheduler.shutdown();
		pool.shutdown();
	}

	/**
	 * Method to start the controller. It starts the periodic refresh and joins the cluster.
	 */
	public void start() {
		startPeriodicRefresh();
		joinCluster();
	}

	/**
	 * Method to list all the containers. It returns a list of all the containers in the cluster.
	 * @return List of all the containers in the cluster.
	 * @throws PicoException if there is an error while fetching the containers.
	 */
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

	/**
	 * Method to fetch a running container.
	 * @param name Name of the container to fetch.
	 * @return Running container with the provided name.
	 * @throws PicoException if there is an error while fetching the container.
	 */
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

	/**
	 * Method to get a container by name.
	 * @param name Name of the container to fetch.
	 * @return Container with the provided name.
	 */
	public PicoContainer getContainer(String name) {
		return cluster.getContainer(name);
	}

	/**
	 * Method to create a container. It creates a container with the provided configuration.
	 * @param container Configuration of the container to create.
	 * @throws PicoException if there is an error while creating the container.
	 */
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

	/**
	 * Method to create a local container. It creates a container with the 
	 * provided configuration in the local node.
	 * @param container Configuration of the container to create.
	 * @return Created container.
	 * @throws PicoException if there is an error while creating the container.
	 */
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

	/**
	 * Method to remove a container. It removes the container with the provided name.
	 * @param name Name of the container to remove.
	 * @throws PicoException if there is an error while removing the container.
	 */
	public void removeContainer(String name) throws PicoException {
		pool.submit(() -> {
				engine.removeContainer(name);
			}
		);
	}

	/**
	 * Method to get the logs of a container. It returns the logs of the 
	 * container with the provided name.
	 * @param name Name of the container to get the logs.
	 * @return Logs of the container with the provided name in the form of a list
	 * 		   of strings.
	 * @throws PicoException if there is an error while fetching the logs.
	 */
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

	/**
	 * Method to start a container. It starts the container with the provided name.
	 * @param name Name of the container to start.
	 * @return Started container.
	 * @throws PicoException if there is an error while starting the container.
	 */
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

	/**
	 * Method to check if a container exists. It returns true if a container
	 * with the provided name exists, otherwise it returns false.
	 * @param name Name of the container to check.
	 * @return True if a container with the provided name exists, otherwise false.
	 */
	public boolean hasContainer(String name) {
		return manager.hasContainerName(name);	
	}

	/**
	 * Method to stop a container. It stops the container with the provided name.
	 * @param name Name of the container to stop.
	 * @throws PicoException if there is an error while stopping the container.
	 */
	public void stopContainer(String name) throws PicoException {
		pool.submit(() -> {
			engine.stopContainer(name);
		});
	}

	/**
	 * Method to restart a container. It restarts the container with the 
	 * provided name.
	 * @param name Name of the container to restart.
	 * @throws PicoException if there is an error while restarting the container.
	 */
	public void restartContainer(String name) throws PicoException {
		pool.submit(() -> {
			engine.restartContainer(name);
		});
	}

	/**
	 * Method to get the current node.
	 * @return Node of the controller.
	 */
	public Node getNode() {
		return manager.getNode();
	}

	/**
	 * Method to get a node by address.
	 * @param address Address of the node to fetch.
	 * @return Node with the provided address.
	 * @throws Exception if there is an error while fetching the node.
	 */
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

	/**
	 * Method to get all the nodes.
	 * @return List of all the nodes in the cluster.
	 * @throws Exception if there is an error while fetching the nodes.
	 */
	public List<Node> getNodes() throws Exception {
			return manager.getNodes();
	}

	/**
	 * Method to get the performance of a node.
	 * @param address Address of the node to fetch the performance.
	 * @return Performance of the node with the provided address.
	 * @throws Exception if there is an error while fetching the performance.
	 */
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

	/**
	 * Method to remove a node. It removes the node with the provided address.
	 * @param adr Address of the node to remove.
	 */
	public void removeNode(PicoAddress adr) {
		manager.removeNode(adr);
	}

	/**
	 * Method to leave the cluster.
	 * @param adr Address of the node to leave the cluster.
	 * @throws Exception if there is an error while leaving the cluster.
	 */
	public void leaveRemote(PicoAddress adr) throws Exception {
		pool.submit(() -> {
			cluster.leaveRemote(adr);
		});
	}

	/**
	 * Method to send a remote command to a container on a different node.
	 * @param containerName Name of the container to send the command.
	 * @param command Command to send to the container.
	 * @return Result of the command.
	 */
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
