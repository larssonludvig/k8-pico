package se.umu.cs.ads.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import se.umu.cs.ads.podengine.PodEngine;
import se.umu.cs.ads.types.*;

@RestController
@RequestMapping("/api")
public class RESTController {
	
	@Autowired
	private PodEngine engine;

	public RESTController() {}

	@GetMapping("/containers")
	public ResponseEntity<List<Pod>> getAllContainers() {
		List<Pod> containers = engine.getContainers();
		return ResponseEntity.status(HttpStatus.OK).body(containers);
	}

	@GetMapping("/containers/{name}")
	public ResponseEntity<Pod> getContainer(@PathVariable String name) {
		Pod container = engine.getContainer(name);
		if (container == null)
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		return ResponseEntity.status(HttpStatus.OK).body(container);
	}

	@PostMapping("/containers")
	@ResponseBody
	public Pod createContainer(@RequestBody Pod container) {
		engine.createContainer(null, null);
	}

	@DeleteMapping("/containers/{name}")
	public void removeContainer(@PathVariable String name) {
	}

	@GetMapping("/containers/{name}/logs")
	@ResponseBody
	public List<String> getContainerLogs(@PathVariable String name) {
		return new ArrayList<String>();
	}

	@PutMapping("/containers/{name}/start")
	@ResponseBody
	public Pod startContainer(@PathVariable String name) {
		return new Pod("hej på dig");
	}

	@PutMapping("/containers/{name}/stop")
	@ResponseBody
	public Pod stopContainer(@PathVariable String name) {
		return new Pod("hej på dig");
	}

	@PutMapping("/containers/{name}/restart")
	public Pod restartContainer(@PathVariable String name) {
		return new Pod("hej på dig");
	}





	
}
