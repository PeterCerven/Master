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

    private final KDominatingSetStrategy kDominatingSetStrategy;
    private final KCentreStrategy kCentreStrategy;

    public ChargingStationPlacementServiceImpl(
            KDominatingSetStrategy kDominatingSetStrategy,
            KCentreStrategy kCentreStrategy) {
        this.kDominatingSetStrategy = kDominatingSetStrategy;
        this.kCentreStrategy = kCentreStrategy;
    }

    @Override
    public PlacementResponseDto computePlacement(RoadGraph graph, PlacementRequestDto request) {
        PlacementStrategy strategy = resolveStrategy(request.getAlgorithm());
        PlacementParams params = PlacementParams.builder()
                .k(request.getK())
                .maxRadiusMeters(request.getMaxRadiusMeters())
                .build();

        log.info("Spustenie algoritmu '{}' s k={} na grafe s {} uzlami a {} hranami",
                request.getAlgorithm(), request.getK(),
                graph.getNodeCount(), graph.getEdgeCount());

        PlacementResult result = strategy.computePlacement(graph, params);

        log.info("Algoritmus '{}' dokončený: vybraných {} staníc, objektívna hodnota = {}",
                request.getAlgorithm(), result.getSelectedNodes().size(), result.getObjectiveValue());

        return PlacementResponseDto.fromResult(result);
    }

    private PlacementStrategy resolveStrategy(PlacementAlgorithm algorithm) {
        return switch (algorithm) {
            case K_DOMINATING_SET -> kDominatingSetStrategy;
            case K_CENTRE -> kCentreStrategy;
        };
    }
}
