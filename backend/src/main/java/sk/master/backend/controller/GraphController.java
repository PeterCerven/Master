package sk.master.backend.controller;

import io.jenetics.jpx.GPX;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.dto.SaveGraphRequest;
import sk.master.backend.persistence.entity.SavedGraph;
import sk.master.backend.persistence.model.MyGraph;
import sk.master.backend.service.FileService;
import sk.master.backend.service.GraphService;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;
    private final FileService fileService;

    public GraphController(GraphService graphService, FileService fileService) {
        this.fileService = fileService;
        this.graphService = graphService;
    }

    @PostMapping("/import")
    public ResponseEntity<MyGraph> parseGpx(@RequestParam("file") MultipartFile file) throws Exception {
        GPX gpx = fileService.parseFile(file);
        MyGraph data = graphService.generateGraph(gpx);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/save")
    public ResponseEntity<SavedGraph> saveGraph(@RequestBody SaveGraphRequest request) {
        SavedGraph savedGraph = graphService.saveGraph(request.getGraph(), request.getName());
        return ResponseEntity.ok(savedGraph);
    }
}
