package se.umu.cs.ads.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import se.umu.cs.ads.podengine.PodEngine;
import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;

@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/api/containers")
public class RESTController {
	
	private PodEngine engine;

	public RESTController() {
		engine = new PodEngine();
	}

	@GetMapping("")
	public ResponseEntity<List<Pod>> getAllContainers() {
		engine.refreshContainers();
		List<Pod> containers = engine.getContainers();
		System.out.println("pointer: " + containers);
		return ResponseEntity.status(HttpStatus.OK).body(containers);
	}

	@GetMapping("{name}")
	public ResponseEntity<Pod> getContainer(@PathVariable String name) {
		Pod container = engine.getContainer(name);
		if (container == null)
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		return ResponseEntity.status(HttpStatus.OK).body(container);
	}

	@PostMapping("")
	@ResponseBody
	public ResponseEntity<?> createContainer(@RequestBody Pod container) {
		try {
			Pod created = engine.createContainer(container);
			return ResponseEntity.ok().body(created);
		} catch (PicoException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
		}
	}

	@DeleteMapping("{name}")
	public void removeContainer(@PathVariable String name) {
	}

	@GetMapping("{name}/logs")
	@ResponseBody
	public List<String> getContainerLogs(@PathVariable String name) {
		return new ArrayList<String>();
	}

	@PutMapping("{name}/start")
	@ResponseBody
	public Pod startContainer(@PathVariable String name) {
		return new Pod("hej på dig");
	}

	@PutMapping("{name}/stop")
	@ResponseBody
	public Pod stopContainer(@PathVariable String name) {
		return new Pod("hej på dig");
	}

	@PutMapping("{name}/restart")
	public Pod restartContainer(@PathVariable String name) {
		return new Pod("hej på dig");
	}





	
}
