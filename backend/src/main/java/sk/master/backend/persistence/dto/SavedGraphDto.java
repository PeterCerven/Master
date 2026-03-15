package sk.master.backend.persistence.dto;

import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.entity.GraphStationEntity;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record SavedGraphDto(
        Long id,
        String name,
        LocalDateTime createdAt,
        List<GraphDto.NodeDto> nodes,
        List<GraphDto.EdgeDto> edges,
        List<StationDto> stations,
        GraphMetricsDto metrics
) {
    public record StationDto(String id, double lat, double lon, int rank) {}

    public static SavedGraphDto fromEntity(GraphEntity entity) {
        List<GraphDto.NodeDto> nodes = entity.getNodes().stream()
                .map(n -> new GraphDto.NodeDto(n.getNodeId(), n.getLat(), n.getLon()))
                .toList();

        List<GraphDto.EdgeDto> edges = entity.getEdges().stream()
                .map(e -> new GraphDto.EdgeDto(e.getSourceId(), e.getTargetId(), e.getDistance()))
                .toList();

        List<StationDto> stations = entity.getStations().stream()
                .sorted(Comparator.comparingInt(GraphStationEntity::getRank))
                .map(s -> new StationDto(s.getStationId(), s.getLat(), s.getLon(), s.getRank()))
                .toList();

        GraphMetricsDto metrics = entity.getMetrics() != null ? entity.getMetrics().toDto() : null;

        return new SavedGraphDto(
                entity.getId(),
                entity.getName(),
                entity.getCreatedAt(),
                nodes,
                edges,
                stations,
                metrics
        );
    }
}
