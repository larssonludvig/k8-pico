package se.umu.cs.ads.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;

import se.umu.cs.ads.types.Node;

@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/api/nodes")
public class NodeController {
	@Autowired
	RESTService service;

    public NodeController() {
    }

    @GetMapping("")
    public ResponseEntity<List<Node>> getNodes() {
        try {
            List<Node> nodes = service.getController().getNodes();
            return ResponseEntity.status(HttpStatus.OK).body(nodes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    @GetMapping("{name}")
    public ResponseEntity<Node> getNode(@PathVariable String name) {
        try {
            Node node = service.getController().getNode(name);
            return ResponseEntity.status(HttpStatus.OK).body(node);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}