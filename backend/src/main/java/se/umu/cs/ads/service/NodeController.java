package se.umu.cs.ads.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.umu.cs.ads.types.Node;
import se.umu.cs.ads.types.Performance;

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
    public ResponseEntity<?> getNode(@PathVariable String name) {
		return ResponseEntity.internalServerError().body(null);
	    // try {
        //     Node node = service.getController().getNode(name);
        //     return ResponseEntity.status(HttpStatus.OK).body(node);
        // } catch (Exception e) {
        //     return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        // }
    }

    @GetMapping("{name}/performance")
    public ResponseEntity<?> getPerformance(@PathVariable String name) {
		return ResponseEntity.internalServerError().body(null);
	    // try {
        //     Performance perf = service.getController().getNodePerformance(name);
        //     return ResponseEntity.status(HttpStatus.OK).body(perf);
        // } catch (Exception e) {
        //     return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        // }
    }
}