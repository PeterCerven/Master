package sk.master.backend.service.placement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sk.master.backend.persistence.dto.PlacementRequestDto;
import sk.master.backend.persistence.dto.PlacementResponseDto;
import sk.master.backend.persistence.model.PlacementAlgorithm;
import sk.master.backend.persistence.model.PlacementParams;
import sk.master.backend.persistence.model.PlacementResult;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.service.placement.strategy.*;

@Service
public class ChargingStationPlacementServiceImpl implements ChargingStationPlacementService {

    private static final Logger log = LoggerFactory.getLogger(ChargingStationPlacementServiceImpl.class);

    private final RandomStrategy randomStrategy;
    private final GreedyStrategy greedyStrategy;
    private final CustomStrategy customStrategy;

    public ChargingStationPlacementServiceImpl(
            RandomStrategy randomStrategy,
            GreedyStrategy greedyStrategy,
            CustomStrategy customStrategy
    ) {
        this.randomStrategy = randomStrategy;
        this.greedyStrategy = greedyStrategy;
        this.customStrategy = customStrategy;
    }

    @Override
    public PlacementResponseDto computePlacement(RoadGraph graph, PlacementRequestDto request) {
        PlacementStrategy strategy = resolveStrategy(request.getAlgorithm());
        PlacementParams params = PlacementParams.builder()
                .k(request.getK())
                .maxRadiusMeters(request.getMaxRadiusMeters())
                .build();

        log.info("Launch algorithm '{}' with k={} na graph with {} nodes and {} edges",
                request.getAlgorithm(), request.getK(),
                graph.getNodeCount(), graph.getEdgeCount());

        PlacementResult result = strategy.computePlacement(graph, params);

        log.info("Algorithm '{}' finished: selected {} charging stations, value = {}",
                request.getAlgorithm(), result.getSelectedNodes().size(), result.getObjectiveValue());

        return PlacementResponseDto.fromResult(result);
    }

    private PlacementStrategy resolveStrategy(PlacementAlgorithm algorithm) {
        return switch (algorithm) {
            case RANDOM_STRATEGY -> randomStrategy;
            case GREEDY_STRATEGY -> greedyStrategy;
            case CUSTOM_STRATEGY -> customStrategy;
        };
    }
}
