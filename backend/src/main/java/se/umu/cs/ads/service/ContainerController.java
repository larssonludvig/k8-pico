package se.umu.cs.ads.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;

import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;

import org.apache.logging.log4j.Logger;


@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/api/containers")
public class ContainerController {
	@Autowired
	RESTService service;
	
	private final static Logger logger = LogManager.getLogger(ContainerController.class);

	@GetMapping("")
	public ResponseEntity<List<PicoContainer>> getAllContainers() {
		List<PicoContainer> containers = service.getController().listAllContainers();
		return ResponseEntity.status(HttpStatus.OK).body(containers);
	}

	@GetMapping("{name}")
	public ResponseEntity<?> getContainer(@PathVariable String name) {
		try {
			PicoContainer container = service.getController().getContainer(name);
			return ResponseEntity.status(HttpStatus.OK).body(container);
		} catch (PicoException e) {
			logger.error("Error while fetching container {}: {}", name, e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}

	@PostMapping("")
	@ResponseBody
	public ResponseEntity<?> createContainer(@RequestBody PicoContainer container) {
		
		try {
			service.getController().createContainer(container);
			return ResponseEntity.ok().body(null);
		} catch (PicoException e) {
			logger.error("Error trying to create container {}: {}", container.getName(), e.getMessage());
			return ResponseEntity.internalServerError().body(e);
		}
	}

	@DeleteMapping("{name}")
	public ResponseEntity<?> removeContainer(@PathVariable String name) {
		
		if (!service.getController().hasContainer(name))
			return ResponseEntity.notFound().build();
		
		try {
			service.getController().removeContainer(name);
			return ResponseEntity.status(HttpStatus.OK).body(null);
		} catch (PicoException e) {
			logger.error("Error trying to remove container {}: {}", name, e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}

	@GetMapping("{name}/logs")
	@ResponseBody
	public ResponseEntity<?> getContainerLogs(@PathVariable String name) {
		try {
			ArrayList<String> logs = new ArrayList<>();
			if (!hasContainer(name)) {
				String logsAsString = service.getController().sendRemoteCommand(name, "FETCH_LOGS");
				logs.addAll(Arrays.asList(logsAsString.split("\n")));
			} else {
				logs.addAll(service.getController().getContainerLogs(name));
			}
			return ResponseEntity.ok(null);
		} catch (Exception e) {
			logger.error("Error trying to retrieve logs for container {}: {}", name, e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}

	@PutMapping("{name}/start")
	@ResponseBody
	public ResponseEntity<?> startContainer(@PathVariable String name) {
		try {
			if (!hasContainer(name)) {
				logger.warn("No container with name: {}", name);
				service.getController().sendRemoteCommand(name, "START");
			} else {
				service.getController().startContainer(name);
			}
		
			return ResponseEntity.status(HttpStatus.OK).body(null);
		} catch (PicoException e) {
			logger.error("Error trying to start container {}: {}", name, e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}

	@PutMapping("{name}/stop")
	@ResponseBody
	public ResponseEntity<?> stopContainer(@PathVariable String name) {
		
		try {
			if (!hasContainer(name)) {
				logger.warn("No container with name {}", name);
				service.getController().sendRemoteCommand(name, "STOP");;
			} else {
				service.getController().stopContainer(name);
			}
			return ResponseEntity.status(HttpStatus.OK).body(null);
		} catch (PicoException e) {
			logger.error("Error trying to stop container {}: {}", name, e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}

	@PutMapping("{name}/restart")
	public ResponseEntity<?> restartContainer(@PathVariable String name) {
		try {
		
		if (!hasContainer(name)) {
			logger.warn("No container with name {}", name);
			service.getController().sendRemoteCommand(name, "RESTART");
		} else {
			service.getController().restartContainer(name);
		}
			return ResponseEntity.status(HttpStatus.OK).body(null);
		} catch (PicoException e) {
			logger.error("Error trying to restart container {}: {}", name, e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMessage());
		}	
	}

	private boolean hasContainer(String name) {
		return service.getController().hasContainer(name);
	}
}
