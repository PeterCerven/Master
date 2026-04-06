package sk.master.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import sk.master.backend.persistence.dto.GraphDto;
import sk.master.backend.persistence.dto.GraphMetricsDto;
import sk.master.backend.persistence.dto.GraphSummaryDto;
import sk.master.backend.persistence.dto.RenameGraphDto;
import sk.master.backend.persistence.dto.SaveGraphDto;
import sk.master.backend.persistence.dto.SavedGraphDto;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.repository.UserRepository;
import sk.master.backend.service.util.FileService;
import sk.master.backend.service.construct.GraphConstructionService;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphConstructionService graphConstructionService;
    private final FileService fileService;
    private final UserRepository userRepository;

    public GraphController(GraphConstructionService graphConstructionService, FileService fileService, UserRepository userRepository) {
        this.fileService = fileService;
        this.graphConstructionService = graphConstructionService;
        this.userRepository = userRepository;
    }

    @PostMapping("/file-import")
    public ResponseEntity<GraphDto> generateGraphFromFile(@RequestParam("file") MultipartFile file) throws Exception {
        List<PositionalData> positionalData = fileService.parseFile(file);
        RoadGraph data = graphConstructionService.generateRoadNetwork(null, positionalData);
        GraphMetricsDto metrics = graphConstructionService.computeMetrics(data);
        return ResponseEntity.ok(GraphDto.fromRoadGraph(data, metrics));
    }

    @GetMapping("/samples")
    public ResponseEntity<List<String>> listSampleFiles() throws IOException {
        return ResponseEntity.ok(fileService.listSampleFiles());
    }

    @PostMapping("/sample-import/{filename}")
    public ResponseEntity<GraphDto> importSampleFile(@PathVariable String filename) throws Exception {
        List<PositionalData> positionalData = fileService.parseSampleFile(filename);
        RoadGraph data = graphConstructionService.generateRoadNetwork(null, positionalData);
        GraphMetricsDto metrics = graphConstructionService.computeMetrics(data);
        return ResponseEntity.ok(GraphDto.fromRoadGraph(data, metrics));
    }

    @GetMapping("/list")
    public ResponseEntity<List<GraphSummaryDto>> listGraphs(Authentication authentication) {
        return ResponseEntity.ok(graphConstructionService.listUserGraphs(resolveUserId(authentication)));
    }

    @GetMapping("/load")
    public ResponseEntity<SavedGraphDto> loadGraphFromDatabase(@RequestParam("graphId") Long graphId, Authentication authentication) {
        return ResponseEntity.ok(graphConstructionService.importGraphFromDatabase(graphId, resolveUserId(authentication)));
    }

    @PostMapping("/save")
    public ResponseEntity<GraphSummaryDto> saveGraph(@RequestBody SaveGraphDto request, Authentication authentication) {
        GraphSummaryDto summary = graphConstructionService.saveGraphToDatabase(
                request.getGraph(), request.getStations(), request.getName(), resolveUserId(authentication));
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("/{graphId}")
    public ResponseEntity<Void> deleteGraph(@PathVariable Long graphId, Authentication authentication) {
        graphConstructionService.deleteGraph(graphId, resolveUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{graphId}/rename")
    public ResponseEntity<GraphSummaryDto> renameGraph(
            @PathVariable Long graphId,
            @RequestBody RenameGraphDto request,
            Authentication authentication) {
        return ResponseEntity.ok(
            graphConstructionService.renameGraph(graphId, request.name(), resolveUserId(authentication))
        );
    }

    private Long resolveUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }
}
