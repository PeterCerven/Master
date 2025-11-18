package sk.master.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.master.backend.persistence.model.MyGraph;
import sk.master.backend.service.GraphService;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/")
    public ResponseEntity<MyGraph> getGraph() {
        MyGraph myGraph = graphService.generateGraph();
        return ResponseEntity.ok(myGraph);
    }
}
