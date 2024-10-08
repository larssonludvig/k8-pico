package se.umu.cs.ads.controller;

import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.util.*;

import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.containerengine.ContainerEngine;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.nodemanager.NodeManager;

import org.jgroups.Address;

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
		try {
			manager.start();
		} catch (Exception e) {
			logger.error("Error while starting node manager: " + e.getMessage());
			System.exit(-1);
		}

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


		scheduler.scheduleAtFixedRate(() -> {
			manager.refreshView();
			logger.debug("Refreshed view");

		}, 0, 5, TimeUnit.SECONDS);
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
			Address leader = manager.getLeader();
			JMessage msg = new JMessage();
			msg.setSender(manager.getChannelAddress());
			msg.setType(MessageType.CONTAINER_ELECTION_START);
			msg.setPayload(container);
			Object reply = manager.send(leader, msg);
			
			if (!(reply instanceof PicoContainer))
				throw new PicoException("Reply not instance of container");
			
			return (PicoContainer) reply;
		});
		try {
			return res.get();
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			String msg = "Error while creating containers " + container.getName() + ": " + e.getMessage();
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
			String msg = "Error while creating containers: " + e.getMessage();
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

	public Node getNode(String name) throws Exception {
		Future<Node> res = pool.submit(() -> {
			return manager.getNode(name);
		});

		try {
			return res.get();
		} catch (Exception e) {
			String msg = "Error while fetching node " + name + ": " + e.getMessage();
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

	public List<Object> broadcast(Object msg) throws Exception {
		Future<List<Object>> res = pool.submit(() -> {
			return manager.broadcast(msg);
		});
		
		try {
			return res.get();
		} catch (Exception e) {
			String err = "Error while broadcasting message: " + e.getMessage();
			logger.error(err);
			throw new Exception(err);
		}
	}

	public Object send(Address address, Object msg) {
		Future<Object> res = pool.submit(() -> {
			return manager.send(address, msg);
		});

		try {
			return res.get();
		} catch (Exception e) {
			String err = "Error while sending message to " + address + ": " + e.getMessage();
			logger.error(err);
			return err;
		}
	}

	public Object send(String name, Object msg) {
		Future<Object> res = pool.submit(() -> {
			Address address = manager.getAddressOfNode(name);
			return manager.send(address, msg);
		});

		try {
			return res.get();
		} catch (Exception e) {
			String err = "Error while sending message to " + name + ": " + e.getMessage();
			logger.error(err);
			return err;
		}
	}
}
