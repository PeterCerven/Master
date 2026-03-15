package sk.master.backend.persistence.dto;

import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

public record GraphDto(
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        GraphMetricsDto metrics
) {
    public record NodeDto(
            String id,
            double lat,
            double lon
    ) {}

    public record EdgeDto(
            String sourceId,
            String targetId,
            double distanceMeters
    ) {}

    public static GraphDto fromRoadGraph(RoadGraph roadGraph, GraphMetricsDto metrics) {
        List<NodeDto> nodes = roadGraph.getNodes().stream()
                .map(node -> new NodeDto(
                        node.getId(),
                        node.getLat(),
                        node.getLon()
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
