package sk.master.backend.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfig {

    // ===== Krok 1: Predspracovanie =====

    private double minLat;
    private double maxLat;
    private double minLon;
    private double maxLon;
    private double nearDuplicateThresholdM;
    private int outlierMinNeighbors;
    private double outlierRadiusM;
    private double maxSpeedKmh;
    private long tripGapMinutes;

    // ===== Krok 2: H3 Spatial Index =====

    private int h3DedupResolution;
    private int h3ClusterResolution;

    // ===== Krok 3: DBSCAN + Graf =====

    private double dbscanEpsMeters;
    private int dbscanMinPts;
    private double maxEdgeLengthM;
    private double mergeThresholdM;
    private int knnK;

    // ===== Krok 4: Map Matching =====

    private double maxSnapDistanceM;
    private boolean removeOffRoadNodes;
}
