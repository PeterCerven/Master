package sk.master.backend.service.placement.strategy;

import org.junit.jupiter.api.Test;
import sk.master.backend.persistence.model.PlacementParams;
import sk.master.backend.persistence.model.PlacementResult;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.model.RoadNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomStrategyTest {

    private final RandomStrategy strategy = new RandomStrategy();

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
                .k(1).maxRadiusMeters(500.0).iterations(5).graspAlpha(0.3).graspEvalBudget(100).build();

        PlacementResult result = strategy.computePlacement(new RoadGraph(), params);

        assertTrue(result.getSelectedNodes().isEmpty());
    }

    @Test
    void feasibleCoverage_smallChain() {
        RoadGraph graph = new RoadGraph();
        List<RoadNode> nodes = linearChain(graph, 5, 100.0);

        PlacementParams params = PlacementParams.builder()
                .k(1).maxRadiusMeters(100.0).iterations(10).graspAlpha(0.3).graspEvalBudget(100).build();

        PlacementResult result = strategy.computePlacement(graph, params);

        assertFalse(result.getSelectedNodes().isEmpty());
        assertEquals(result.getSelectedNodes().size(), (int) result.getObjectiveValue());

        for (RoadNode node : nodes) {
            double d = result.getNodeDistances().get(node.getId());
            assertTrue(d >= 0 && d <= 100.0,
                    "Node " + node.getId() + " uncovered (distance=" + d + ")");
        }
    }
}
