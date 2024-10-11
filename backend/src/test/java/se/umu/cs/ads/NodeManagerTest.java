// package se.umu.cs.ads;

// import org.junit.jupiter.api.Test;

// import se.umu.cs.ads.controller.Controller;
// import se.umu.cs.ads.nodemanager.NodeManager;
// import se.umu.cs.ads.types.PicoContainer;

// import static org.junit.jupiter.api.Assertions.assertFalse;
// import static org.junit.jupiter.api.Assertions.assertTrue;

// import java.util.*;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;


// public class NodeManagerTest {

// 	private NodeManager manager;
// 	private Controller controller;

// 	@BeforeEach
// 	public void prepare() {
// 		this.controller = new Controller();
// 		this.manager = new NodeManager(controller, "k8-test");
// 	}

// 	@AfterEach
// 	public void cleanup() {
// 		controller.shutdown();
// 		manager = null;
// 		controller = null;
// 	}

// 	@Test
// 	public void updateRemoteContainerTest() {
// 		PicoContainer c1 = new PicoContainer().setName("test-1").setImage("img-1");
// 		PicoContainer c2 = new PicoContainer("test-2");

// 		List<PicoContainer> existing = manager.getRemoteContainers("host1");

// 		assertFalse(existing.contains(c1), "No containers have been added yet so should be empty!");
// 		assertFalse(existing.contains(c2), "No containers have been added yet so should be empty!");

// 		manager.updateRemoteContainers("host1", c1);
// 		assertTrue(manager.getRemoteContainers("host1").contains(c1), "Added container has not been added");

// 		manager.updateRemoteContainers("host1", c2);
// 		assertTrue(manager.getRemoteContainers("host1").contains(c2), "Added container has not been added");

// 	}

// 	@Test
// 	public void updateRemoteContainerBatchTest() {
// 		PicoContainer c1 = new PicoContainer().setName("test-1").setImage("img-1");
// 		PicoContainer c2 = new PicoContainer("test-2");

// 		List<PicoContainer> list = new ArrayList<>();
// 		list.add(c1);
// 		list.add(c2);


// 		assertFalse(manager.getRemoteContainers("host1").contains(c1), "List was not empty");
// 		assertFalse(manager.getRemoteContainers("host1").contains(c2), "List was not empty");

// 		manager.updateRemoteContainers("host1", list);
// 		assertTrue(manager.getRemoteContainers("host1").contains(c1), "Added container has not been added");
// 		assertTrue(manager.getRemoteContainers("host1").contains(c2), "Added container has not been added");

// 	}

// }
