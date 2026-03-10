package sk.master.backend.persistence.dto;

import sk.master.backend.persistence.entity.GraphEntity;

import java.time.LocalDateTime;

public record GraphSummaryDto(
        Long id,
        String name,
        LocalDateTime createdAt,
        int nodeCount,
        int edgeCount,
        int stationCount
) {
    public static GraphSummaryDto fromEntity(GraphEntity entity) {
        return new GraphSummaryDto(
                entity.getId(),
                entity.getName(),
                entity.getCreatedAt(),
                entity.getNodes().size(),
                entity.getEdges().size(),
                entity.getStations().size()
        );
    }
}