package sk.master.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nullable — when null, this is a global configuration. Prepared for future per-user configs. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    // ===== Krok 1: Predspracovanie =====

    @Column(name = "min_lat", nullable = false)
    private double minLat;

    @Column(name = "max_lat", nullable = false)
    private double maxLat;

    @Column(name = "min_lon", nullable = false)
    private double minLon;

    @Column(name = "max_lon", nullable = false)
    private double maxLon;

    @Column(name = "near_duplicate_threshold_m", nullable = false)
    private double nearDuplicateThresholdM;

    @Column(name = "outlier_min_neighbors", nullable = false)
    private int outlierMinNeighbors;

    @Column(name = "outlier_radius_m", nullable = false)
    private double outlierRadiusM;

    @Column(name = "max_speed_kmh", nullable = false)
    private double maxSpeedKmh;

    @Column(name = "trip_gap_minutes", nullable = false)
    private long tripGapMinutes;

    // ===== Krok 2: H3 Spatial Index =====

    @Column(name = "h3_dedup_resolution", nullable = false)
    private int h3DedupResolution;

    @Column(name = "h3_cluster_resolution", nullable = false)
    private int h3ClusterResolution;

    // ===== Krok 3: DBSCAN + Graf =====

    @Column(name = "dbscan_eps_meters", nullable = false)
    private double dbscanEpsMeters;

    @Column(name = "dbscan_min_pts", nullable = false)
    private int dbscanMinPts;

    @Column(name = "max_edge_length_m", nullable = false)
    private double maxEdgeLengthM;

    @Column(name = "merge_threshold_m", nullable = false)
    private double mergeThresholdM;

    @Column(name = "knn_k", nullable = false)
    private int knnK;

    // ===== Krok 4: Map Matching =====

    @Column(name = "max_snap_distance_m", nullable = false)
    private double maxSnapDistanceM;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
