package sk.master.backend.persistence.dto;

import org.jgrapht.alg.connectivity.ConnectivityInspector;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.model.RoadNode;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GraphDto(
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        GraphMetricsDto metrics
) {
    public record NodeDto(
            String id,
            double lat,
            double lon,
            Integer componentId
    ) {}

    public record EdgeDto(
            String sourceId,
            String targetId,
            double distanceMeters
    ) {}

    public static GraphDto fromRoadGraph(RoadGraph roadGraph, GraphMetricsDto metrics) {
        var inspector = new ConnectivityInspector<>(roadGraph.getGraph());
        List<Set<RoadNode>> components = inspector.connectedSets().stream()
                .sorted(Comparator.comparingInt((Set<RoadNode> s) -> s.size()).reversed())
                .toList();

        Map<String, Integer> componentIdByNodeId = new HashMap<>();
        for (int i = 0; i < components.size(); i++) {
            int componentId = i;
            components.get(i).forEach(node -> componentIdByNodeId.put(node.getId(), componentId));
        }

        List<NodeDto> nodes = roadGraph.getNodes().stream()
                .map(node -> new NodeDto(
                        node.getId(),
                        node.getLat(),
                        node.getLon(),
                        componentIdByNodeId.getOrDefault(node.getId(), 0)
                ))
                .toList();

        List<EdgeDto> edges = roadGraph.getEdges().stream()
                .map(edge -> new EdgeDto(
                        edge.sourceId(),
                        edge.targetId(),
                        edge.distanceMeters()
                ))
                .toList();

        return new GraphDto(nodes, edges, metrics);
    }
}
