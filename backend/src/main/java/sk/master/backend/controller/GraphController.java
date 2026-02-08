package sk.master.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.dto.AddPointsRequest;
import sk.master.backend.persistence.dto.SaveGraphRequest;
import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.service.FileService;
import sk.master.backend.service.GraphService;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;
    private final FileService fileService;

    public GraphController(GraphService graphService, FileService fileService) {
        this.fileService = fileService;
        this.graphService = graphService;
    }

    @PostMapping("/file-import")
    public ResponseEntity<RoadGraph> generateGraphFromFile(@RequestParam("file") MultipartFile file) throws Exception {
        List<PositionalData> positionalData = fileService.parseFile(file);
        RoadGraph data = graphService.generateRoadNetwork(null, positionalData);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/load")
    public ResponseEntity<RoadGraph> loadGraphFromDatabase(@RequestParam("graphId") Long graphId) {
        RoadGraph data = graphService.importGraphFromDatabase(graphId);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/save")
    public ResponseEntity<GraphEntity> saveGraph(@RequestBody SaveGraphRequest request) {
        GraphEntity graphEntity = graphService.saveGraphToDatabase(request.getGraph(), request.getName());
        return ResponseEntity.ok(graphEntity);
    }

    @PostMapping("/add-points")
    public ResponseEntity<RoadGraph> updateGraphWithPoints(@RequestBody AddPointsRequest request) {
        RoadGraph graph = graphService.generateRoadNetwork(request.getGraph(), request.getPositionalData());
        return ResponseEntity.ok(graph);
    }
}
