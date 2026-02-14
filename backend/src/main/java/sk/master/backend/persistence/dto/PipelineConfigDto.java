package sk.master.backend.persistence.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfigDto {

    private Long id;
    private String name;

    // Predspracovanie
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private double minLat;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private double maxLat;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private double minLon;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private double maxLon;

    @Positive
    private double nearDuplicateThresholdM;

    @Min(1)
    private int outlierMinNeighbors;

    @Positive
    private double outlierRadiusM;

    @Positive
    private double maxSpeedKmh;

    @Min(1)
    private long tripGapMinutes;

    // H3
    @Min(0) @Max(15)
    private int h3DedupResolution;

    @Min(0) @Max(15)
    private int h3ClusterResolution;

    private boolean h3AdaptiveEnabled;

    @Min(0) @Max(15)
    private int h3DedupResolutionUrban;

    @Min(2)
    private int h3AdaptiveDensityThreshold;

    // DBSCAN + Graf
    @Positive
    private double dbscanEpsMeters;

    @Min(1)
    private int dbscanMinPts;

    @Positive
    private double maxEdgeLengthM;

    @Positive
    private double mergeThresholdM;

    @Min(1)
    private int knnK;

    @DecimalMin("0") @DecimalMax("180")
    private double maxBearingDiffDeg;

    // Map Matching
    @Positive
    private double maxSnapDistanceM;

    private boolean removeOffRoadNodes;
}
