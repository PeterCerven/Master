package sk.master.backend.service.placement.strategy;

import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.PlacementParams;
import sk.master.backend.persistence.model.PlacementResult;
import sk.master.backend.persistence.model.RoadGraph;

@Component
public class CustomStrategy implements PlacementStrategy {
    @Override
    public PlacementResult computePlacement(RoadGraph graph, PlacementParams params) {
        return null;
    }
}
