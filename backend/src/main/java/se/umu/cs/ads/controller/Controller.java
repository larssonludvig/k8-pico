package se.umu.cs.ads.controller;

import java.util.concurrent.*;

import org.apache.commons.io.DirectoryWalker.CancelException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.ExceptionDepthComparator;
import org.springframework.scheduling.concurrent.ScheduledExecutorFactoryBean;

import javassist.bytecode.CodeAttribute.RuntimeCopyException;

import java.util.*;

import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.podengine.PodEngine;
import se.umu.cs.ads.service.RESTService;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.nodemanager.NodeManager;

import org.jgroups.Address;

public class Controller {
	private final ExecutorService pool;
	private final PodEngine engine;
	private final static Logger logger = LogManager.getLogger(Controller.class.getName());
	private final ScheduledExecutorService scheduler;
	private final NodeManager manager;

	private int port;


	public Controller() {
		pool = Executors.newCachedThreadPool();
		scheduler = Executors.newScheduledThreadPool(1);
		engine = new PodEngine();
		
		System.out.print("What is the name of this node? ");
		String line = System.console().readLine();
		manager = new NodeManager(this);

		try {
			manager.start("k8-pico", line);
		} catch (Exception e) {
			logger.error("Error while starting node manager: " + e.getMessage());
			System.exit(1);
		}

		startPeriodicRefresh();
	}

	public void setPort(int port) {
		this.port = port;
		this.manager.node.setPort(port);
	}

	public int getPort() {
		return this.port;
	}

	// Pod engine methods ----------------------------------------------------------

	private void startPeriodicRefresh() {
		scheduler.scheduleAtFixedRate(() -> {
			long start = System.currentTimeMillis();
			logger.info("Refreshing containers from system...");
			engine.readContainers(true);
			engine.readImages();
			long time = System.currentTimeMillis() - start;
			logger.info("Refreshed containers and images in " + time + "ms");
		}, 4, 4, TimeUnit.SECONDS);
	}

	public void shutdown() {
		scheduler.shutdown();
		pool.shutdown();
	}

	public List<Pod> listAllContainers() throws PicoException {
		Future<List<Pod>> res = pool.submit(() -> {
			return engine.getContainers(false);
			});

		try {
			return res.get();
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			String msg = "Error while fetching containers: " + e.getMessage();
			logger.error(msg);
			throw new PicoException(msg);
		}
	}


	public Pod getRunningContainer(String name) throws PicoException {
		Future<Pod> res = pool.submit(() ->  {
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

	public Pod createContainer(Pod container) throws PicoException {
		Future<Pod> res = pool.submit(() -> {
			return engine.createContainer(container);
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

	public Pod startContainer(String name) throws PicoException {
		Future<Pod> res = pool.submit(() -> {
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
