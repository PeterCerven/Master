package sk.master.backend.service.placement;

import sk.master.backend.persistence.dto.PlacementRequestDto;
import sk.master.backend.persistence.dto.PlacementResponseDto;
import sk.master.backend.persistence.model.RoadGraph;

public interface ChargingStationPlacementService {

    PlacementResponseDto computePlacement(RoadGraph graph, PlacementRequestDto request);
}
