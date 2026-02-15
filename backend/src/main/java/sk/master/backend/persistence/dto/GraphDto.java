package sk.master.backend.persistence.dto;

import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

public record GraphDto(
        List<NodeDto> nodes,
        List<EdgeDto> edges
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

    public static GraphDto fromRoadGraph(RoadGraph roadGraph) {
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

        return new GraphDto(nodes, edges);
    }

    public static GraphDto fromGraphEntity(GraphEntity graphEntity) {
        List<NodeDto> nodes = graphEntity.getNodes().stream()
                .map(node -> new NodeDto(
                        node.getNodeId(),
                        node.getLat(),
                        node.getLon()
                ))
                .toList();

        List<EdgeDto> edges = graphEntity.getEdges().stream()
                .map(edge -> new EdgeDto(
                        edge.getSourceId(),
                        edge.getTargetId(),
                        edge.getDistance()
                ))
                .toList();

        return new GraphDto(nodes, edges);
    }
}
