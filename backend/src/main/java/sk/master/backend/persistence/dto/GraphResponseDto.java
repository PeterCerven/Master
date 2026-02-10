package sk.master.backend.persistence.dto;

import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

public record GraphResponseDto(
        List<NodeDto> nodes,
        List<EdgeDto> edges
) {
    public record NodeDto(
            String id,
            double lat,
            double lon,
            String roadName,
            String roadClass
    ) {}

    public record EdgeDto(
            String sourceId,
            String targetId,
            double distanceMeters,
            String roadName
    ) {}

    public static GraphResponseDto fromRoadGraph(RoadGraph roadGraph) {
        List<NodeDto> nodes = roadGraph.getNodes().stream()
                .map(node -> new NodeDto(
                        node.getId(),
                        node.getLat(),
                        node.getLon(),
                        node.getRoadName(),
                        node.getRoadClass() != null ? node.getRoadClass().name() : null
                ))
                .toList();

        List<EdgeDto> edges = roadGraph.getEdges().stream()
                .map(edge -> new EdgeDto(
                        edge.getSourceId(),
                        edge.getTargetId(),
                        edge.getDistanceMeters(),
                        edge.getRoadName()
                ))
                .toList();

        return new GraphResponseDto(nodes, edges);
    }
}
