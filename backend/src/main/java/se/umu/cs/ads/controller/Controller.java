package se.umu.cs.ads.controller;

import java.util.*;
import java.util.concurrent.*;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.containerengine.ContainerEngine;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.nodemanager.NodeManager;

public class Controller {
	private final ExecutorService pool;
	private final ContainerEngine engine;
	private final static Logger logger = LogManager.getLogger(Controller.class);
	private final ScheduledExecutorService scheduler;
	private final NodeManager manager;

	public Controller() {
		pool = Executors.newCachedThreadPool();
		scheduler = Executors.newScheduledThreadPool(2);
		engine = new ContainerEngine();
		manager = new NodeManager(this, "k8-pico");
		manager.setActiveContainers(engine.getContainers(true));

		startPeriodicRefresh();
	}


	// Pod engine methods ----------------------------------------------------------

	private void startPeriodicRefresh() {
		scheduler.scheduleAtFixedRate(() -> {
			long start = System.currentTimeMillis();
			Map<String, PicoContainer> containers = engine.readContainers(true);
			List<String> images = engine.readImages();
			engine.setContainers(containers);
			engine.setImages(images);
			long time = System.currentTimeMillis() - start;
			manager.setActiveContainers(new ArrayList<PicoContainer>(containers.values()));
			logger.debug("Refreshed containers and images in {} ms", time);

		}, 5, 5, TimeUnit.SECONDS);
	}

	public void shutdown() {
		scheduler.shutdown();
		pool.shutdown();
	}

	public List<PicoContainer> listAllContainers() throws PicoException {
		Future<List<PicoContainer>> res = pool.submit(() -> {
			return engine.getContainers(true);
			});

		try {
			return res.get();
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			String msg = "Error while fetching containers: " + e.getMessage();
			logger.error(msg);
			throw new PicoException(msg);
		}
	}


	public PicoContainer getRunningContainer(String name) throws PicoException {
		Future<PicoContainer> res = pool.submit(() ->  {
			return engine.getContainer(name);
		});

		try {
			return res.get();
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			String msg = "Error while retrieving container " + name + ": " + e.getMessage();
			logger.error(msg);
			throw new PicoException(msg);
		}
	}

	public PicoContainer createContainer(PicoContainer container) throws PicoException {
		Future<PicoContainer> res = pool.submit(() -> {
			InetSocketAddress leader = manager.getLeader();

			JMessage msg = new JMessage();
			msg.setType(MessageType.CONTAINER_ELECTION_START);
			msg.setPayload(container);
			JMessage reply = manager.send(leader, msg);
			Object payload = reply.getPayload();
			
			if (!(payload instanceof PicoContainer))
				throw new PicoException("Reply not instance of container");
			
			return (PicoContainer) payload;
		});
		try {
			return res.get(1, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			logger.info("Timeout exceded, but we do not care");
			return null;
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			String msg = "Error while creating containers " + container.getName() + ": " + e.getMessage();
			e.printStackTrace();
			logger.error(msg);
			throw new PicoException(msg);
		}
	}

	public PicoContainer createLocalContainer(PicoContainer container) throws PicoException {
		Future<PicoContainer> res = pool.submit(() -> {
			return engine.createContainer(container);
		});

		try {
			return res.get();
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			String msg = "Error while creating local container: " + e.getMessage();
			logger.error(msg);
			throw new PicoException(msg);
		}
	}

	public void removeContainer(String name) throws PicoException {
		pool.submit(() -> {
				engine.removeContainer(name);
			}
		);
	}

	public List<String> getContainerLogs(String name) throws Exception {
		Future<List<String>> res = pool.submit(() -> {
			return engine.containerLog(name);
		});

		try {
			return res.get();
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			String msg = "Error while fetching container logs: " + e.getMessage();
			logger.error(msg);
			throw new PicoException(msg);
		}
	}

	public PicoContainer startContainer(String name) throws PicoException {
		Future<PicoContainer> res = pool.submit(() -> {
			return engine.runContainer(name);
		});

		try {
			return res.get();
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			String msg = "Error while starting container " + name + ": " + e.getMessage();
			logger.error(msg);
			throw new PicoException(msg);
		}
	}


	public boolean hasContainer(String name) {
		Future<Boolean> res = pool.submit(() -> {
			return engine.hasContainer(name);
		});

		try {
			return res.get();
		} catch (Exception e) {
			return false;
		}
	
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

	public Node getNode(InetSocketAddress ipPort) throws Exception {
		Future<Node> res = pool.submit(() -> {
			return manager.getNode(ipPort);
		});

		try {
			return res.get();
		} catch (Exception e) {
			String msg = "Error while fetching node " + ipPort + ": " + e.getMessage();
			logger.error(msg);
			throw new Exception(msg);
		}
	}

	public List<Node> getNodes() throws Exception {
		Future<List<Node>> res = pool.submit(() -> {
			return manager.getNodes();
		});

		try {
			return res.get();
		} catch (Exception e) {
			String msg = "Error while fetching nodes: " + e.getMessage();
			logger.error(msg);
			throw new Exception(msg);
		}
	}

	public Performance getNodePerformance(InetSocketAddress ipPort) throws Exception {
		Future<Performance> res = pool.submit(() -> {
			return manager.getNodePerformance(ipPort);
		});

		try {
			return res.get();
		} catch (Exception e) {
			String msg = "Error while fetching node performance: " + e.getMessage();
			logger.error(msg);
			throw new Exception(msg);
		}
	}

	public ExecutorService getPool() {
		return this.pool;
	}
}
