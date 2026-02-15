package sk.master.backend.service.placement.strategy;

import sk.master.backend.persistence.model.PlacementParams;
import sk.master.backend.persistence.model.PlacementResult;
import sk.master.backend.persistence.model.RoadGraph;

public interface PlacementStrategy {

    PlacementResult computePlacement(RoadGraph graph, PlacementParams params);
}
