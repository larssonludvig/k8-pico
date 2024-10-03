package se.umu.cs.ads.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;

@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/api/containers")
public class ContainerController {
	@Autowired
	RESTService service;
	
	@GetMapping("")
	public ResponseEntity<List<PicoContainer>> getAllContainers() {
		List<PicoContainer> containers = service.getController().listAllContainers();
		return ResponseEntity.status(HttpStatus.OK).body(containers);
	}

	@GetMapping("{name}")
	public ResponseEntity<PicoContainer> getContainer(@PathVariable String name) {
		PicoContainer container = service.getController().getRunningContainer(name);
		if (container == null)
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		return ResponseEntity.status(HttpStatus.OK).body(container);
	}

	@PostMapping("")
	@ResponseBody
	public ResponseEntity<?> createContainer(@RequestBody PicoContainer container) {
		try {
			PicoContainer created = service.getController().createContainer(container);
			return ResponseEntity.ok().body(created);
		} catch (PicoException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e);
		}
	}

	@DeleteMapping("{name}")
	public ResponseEntity<Void> removeContainer(@PathVariable String name) {
		try {
			service.getController().removeContainer(name);

			if (service.getController().getRunningContainer(name) != null) 
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
			
			return ResponseEntity.status(HttpStatus.OK).body(null);
		} catch (PicoException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

	@GetMapping("{name}/logs")
	@ResponseBody
	public ResponseEntity<List<String>> getContainerLogs(@PathVariable String name) {
		try {
			List<String> logs = service.getController().getContainerLogs(name);
			return ResponseEntity.status(HttpStatus.OK).body(logs);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

	@PutMapping("{name}/start")
	@ResponseBody
	public ResponseEntity<PicoContainer> startContainer(@PathVariable String name) {
		try {
			PicoContainer container = service.getController().startContainer(name);
			return ResponseEntity.status(HttpStatus.OK).body(container);
		} catch (PicoException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

	@PutMapping("{name}/stop")
	@ResponseBody
	public ResponseEntity<Void> stopContainer(@PathVariable String name) {
		try {
			service.getController().stopContainer(name);
			return ResponseEntity.status(HttpStatus.OK).body(null);
		} catch (PicoException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

	@PutMapping("{name}/restart")
	public ResponseEntity<Void> restartContainer(@PathVariable String name) {
		try {
			service.getController().restartContainer(name);
			return ResponseEntity.status(HttpStatus.OK).body(null);
		} catch (PicoException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}	
	}
}
