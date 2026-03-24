package sk.master.backend.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.dto.GraphMetricsDto;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphMetricsEmbeddable {
    private Integer nodeCount;
    private Integer edgeCount;
    private Double avgDegree;
    private Double diameterMeters;
    private Double clusteringCoefficient;
    private Double avgEdgeLengthMeters;
    private Double nodeDensityPerKm2;
    private Boolean connected;
    private Double radiusMeters;
    private Double avgBetweennessCentrality;
    private Integer treewidth;

    public static GraphMetricsEmbeddable fromDto(GraphMetricsDto dto) {
        return new GraphMetricsEmbeddable(
                dto.nodeCount(),
                dto.edgeCount(),
                dto.avgDegree(),
                dto.diameterMeters(),
                dto.clusteringCoefficient(),
                dto.avgEdgeLengthMeters(),
                dto.nodeDensityPerKm2(),
                dto.connected(),
                dto.radiusMeters(),
                dto.avgBetweennessCentrality(),
                dto.treewidth()
        );
    }

    public GraphMetricsDto toDto() {
        return new GraphMetricsDto(
                nodeCount,
                edgeCount,
                avgDegree,
                diameterMeters,
                clusteringCoefficient,
                avgEdgeLengthMeters,
                nodeDensityPerKm2,
                connected,
                radiusMeters,
                avgBetweennessCentrality,
                treewidth != null ? treewidth : 0
        );
    }
}
