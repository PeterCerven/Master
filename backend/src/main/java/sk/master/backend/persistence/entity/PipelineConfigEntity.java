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

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "min_lat", nullable = false)
    private double minLat;

    @Column(name = "max_lat", nullable = false)
    private double maxLat;

    @Column(name = "min_lon", nullable = false)
    private double minLon;

    @Column(name = "max_lon", nullable = false)
    private double maxLon;

    @Column(name = "max_speed_kmh", nullable = false)
    private double maxSpeedKmh;

    @Column(name = "h3_dedup_resolution", nullable = false)
    private int h3DedupResolution;

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
