package sk.master.backend.service.placement.strategy;

import org.junit.jupiter.api.Test;
import sk.master.backend.persistence.model.PlacementParams;
import sk.master.backend.persistence.model.PlacementResult;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.model.RoadNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedyStrategyTest {

    private final GreedyStrategy strategy = new GreedyStrategy();

    private List<RoadNode> linearChain(RoadGraph graph, int n, double edgeMeters) {
        List<RoadNode> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RoadNode node = new RoadNode(48.0 + i * 0.001, 17.0 + i * 0.001);
            graph.addNode(node);
            nodes.add(node);
        }
        for (int i = 0; i < n - 1; i++) {
            graph.addEdge(nodes.get(i), nodes.get(i + 1), edgeMeters);
        }
        return nodes;
    }

    @Test
    void emptyGraph_returnsEmptyResult() {
        PlacementParams params = PlacementParams.builder()
                .k(1).maxRadiusMeters(500.0).iterations(1).graspAlpha(0.3).graspEvalBudget(100).build();

        PlacementResult result = strategy.computePlacement(new RoadGraph(), params);

        assertTrue(result.getSelectedNodes().isEmpty());
        assertEquals(0, result.getObjectiveValue());
    }

    @Test
    void linearChain_k1_radiusCoversAll_returnsOneStation() {
        RoadGraph graph = new RoadGraph();
        linearChain(graph, 5, 100.0);

        PlacementParams params = PlacementParams.builder()
                .k(1).maxRadiusMeters(500.0).iterations(1).graspAlpha(0.3).graspEvalBudget(100).build();

        PlacementResult result = strategy.computePlacement(graph, params);

        assertEquals(1, result.getSelectedNodes().size(),
                "One station with radius >= chain length must cover all nodes");
    }

    @Test
    void linearChain_k1_smallRadius_requiresMultipleStations() {
        RoadGraph graph = new RoadGraph();
        List<RoadNode> nodes = linearChain(graph, 5, 100.0);

        PlacementParams params = PlacementParams.builder()
                .k(1).maxRadiusMeters(100.0).iterations(1).graspAlpha(0.3).graspEvalBudget(100).build();

        PlacementResult result = strategy.computePlacement(graph, params);

        assertTrue(result.getSelectedNodes().size() >= 2,
                "Small radius forces multiple stations, got " + result.getSelectedNodes().size());

        for (RoadNode node : nodes) {
            double minDistanceToStation = result.getNodeDistances().get(node.getId());
            assertTrue(minDistanceToStation >= 0 && minDistanceToStation <= 100.0,
                    "Node " + node.getId() + " uncovered (distance=" + minDistanceToStation + ")");
        }
    }
}
