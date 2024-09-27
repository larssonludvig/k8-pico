package se.umu.cs.ads.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import se.umu.cs.ads.nodemanager.NodeManager;
import se.umu.cs.ads.types.Node;

@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/api/nodes")
public class NodeController {
    private NodeManager nodeManager;

    public NodeController() {
        nodeManager = new NodeManager();
        System.out.print("What is the name of this node? ");
        String line = System.console().readLine();

        try {
            nodeManager.start("k8-pico", line);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("")
    public ResponseEntity<List<Node>> getNodes() {
        List<Node> nodes = nodeManager.getNodes();
        if (nodes == null || nodes.size() <= 0)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        return ResponseEntity.status(HttpStatus.OK).body(nodes);
    }

    @GetMapping("{name}")
    public ResponseEntity<Node> getNode(@PathVariable String name) {
        Node node = nodeManager.getNode(name);
        if (node == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        return ResponseEntity.status(HttpStatus.OK).body(node);
    }
}