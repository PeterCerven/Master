package sk.master.backend.service.placement.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.PlacementParams;
import sk.master.backend.persistence.model.PlacementResult;
import sk.master.backend.persistence.model.RoadGraph;


@Component
public class GreedyStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(GreedyStrategy.class);

    @Override
    public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
        return null;
    }

}
