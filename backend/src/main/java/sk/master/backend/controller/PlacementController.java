package sk.master.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sk.master.backend.persistence.dto.PlacementRequestDto;
import sk.master.backend.persistence.dto.PlacementResponseDto;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.service.placement.ChargingStationPlacementService;

@RestController
@RequestMapping("/api/placement")
public class PlacementController {

    private final ChargingStationPlacementService placementService;

    public PlacementController(ChargingStationPlacementService placementService) {
        this.placementService = placementService;
    }

    @PostMapping("/compute")
    public ResponseEntity<PlacementResponseDto> computePlacement(
            @Valid @RequestBody PlacementRequestDto request) {
        RoadGraph graph = RoadGraph.fromDto(request.getGraph());
        PlacementResponseDto response = placementService.computePlacement(graph, request);
        return ResponseEntity.ok(response);
    }
}
