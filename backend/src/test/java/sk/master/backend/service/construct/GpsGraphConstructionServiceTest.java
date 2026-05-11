package sk.master.backend.service.construct;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.master.backend.persistence.model.PipelineConfig;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.model.RoadNode;
import sk.master.backend.persistence.repository.GraphRepository;
import sk.master.backend.service.util.PipelineConfigService;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GpsGraphConstructionServiceTest {

    private MapMatchingService mapMatching;
    private PipelineConfigService configService;
    private GraphRepository graphRepository;
    private OsmCityGraphService osmCityGraphService;

    @BeforeEach
    void setUp() {
        mapMatching = mock(MapMatchingService.class);
        configService = mock(PipelineConfigService.class);
        graphRepository = mock(GraphRepository.class);
        osmCityGraphService = mock(OsmCityGraphService.class);

        when(mapMatching.matchTrajectory(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** H3 res 13 cells are ~1.2 m wide — points spaced tens of meters land in different cells. */
    private GpsGraphConstructionService serviceWithConfig(double maxSpeedKmh, int h3DedupResolution) {
        when(configService.getActivePipelineConfig())
                .thenReturn(new PipelineConfig(maxSpeedKmh, h3DedupResolution, "Slovakia", 0.02, 0.0));
        return new GpsGraphConstructionService(graphRepository, configService, mapMatching, osmCityGraphService);
    }

    @Test
    void emptyInput_returnsEmptyGraph() {
        GpsGraphConstructionService service = serviceWithConfig(200, 13);

        RoadGraph result = service.generateRoadNetwork(null, List.of());

        assertEquals(0, result.getNodeCount());
        assertEquals(0, result.getEdgeCount());
    }

    @Test
    void invalidCoordinates_areFilteredOut() {
        GpsGraphConstructionService service = serviceWithConfig(200, 13);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");

        // Two valid Slovak points + one out-of-range latitude + one (0,0) island point.
        List<PositionalData> input = List.of(
                new PositionalData(48.0000, 17.0000, t0, 1),
                new PositionalData(95.0000, 17.0001, t0.plusSeconds(1), 1),    // invalid lat
                new PositionalData(0.0000,  0.0000,  t0.plusSeconds(2), 1),    // (0,0) island
                new PositionalData(48.0001, 17.0001, t0.plusSeconds(3), 1)
        );

        RoadGraph result = service.generateRoadNetwork(null, input);

        assertEquals(2, result.getNodeCount());
        assertEquals(1, result.getEdgeCount());
    }

    @Test
    void speedOutliers_areRemovedFromTrip() {
        GpsGraphConstructionService service = serviceWithConfig(200, 13);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");

        List<PositionalData> input = List.of(
                new PositionalData(48.0000, 17.0000, t0, 1),
                new PositionalData(48.5000, 17.5000, t0.plusSeconds(1), 1),     // outlier
                new PositionalData(48.0001, 17.0001, t0.plusSeconds(2), 1)
        );

        RoadGraph result = service.generateRoadNetwork(null, input);

        assertEquals(2, result.getNodeCount(),
                "Outlier middle point must be removed, leaving the first and third only");
        assertEquals(1, result.getEdgeCount());
    }

    @Test
    void differentTripIds_areProcessedSeparately() {
        GpsGraphConstructionService service = serviceWithConfig(200, 13);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");

        List<PositionalData> input = List.of(
                new PositionalData(48.0000, 17.0000, t0, 1),
                new PositionalData(48.0001, 17.0001, t0.plusSeconds(10), 1),
                new PositionalData(49.5000, 18.5000, t0, 2),
                new PositionalData(49.5001, 18.5001, t0.plusSeconds(10), 2)
        );

        RoadGraph result = service.generateRoadNetwork(null, input);

        assertEquals(4, result.getNodeCount());
        assertEquals(2, result.getEdgeCount(), "Each trip contributes exactly one edge; trips are disjoint");
    }

    @Test
    void h3Merge_collapsesNearbyNodesIntoSingleMaster() {
        GpsGraphConstructionService service = serviceWithConfig(200, 5);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");

        List<PositionalData> input = List.of(
                new PositionalData(48.0000, 17.0000, t0, 1),
                new PositionalData(48.0001, 17.0001, t0.plusSeconds(10), 1)
        );

        RoadGraph result = service.generateRoadNetwork(null, input);

        assertEquals(1, result.getNodeCount(), "Two overlapping nodes collapse to one master node");
        assertEquals(0, result.getEdgeCount(), "Intra-cell edge between merged nodes is dropped");
    }

    @Test
    void mapMatchingFailure_fallsBackToRawGpsAndMarksOffRoad() {
        GpsGraphConstructionService service = serviceWithConfig(200, 13);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");

        when(mapMatching.matchTrajectory(anyList())).thenReturn(null);

        List<PositionalData> input = List.of(
                new PositionalData(48.0000, 17.0000, t0, 1),
                new PositionalData(48.0001, 17.0001, t0.plusSeconds(10), 1)
        );

        RoadGraph result = service.generateRoadNetwork(null, input);

        assertEquals(2, result.getNodeCount());
        for (RoadNode node : result.getNodes()) {
            assertTrue(node.isOffRoad(), "All inserted nodes from fallback path must be marked off-road");
        }
    }
}
