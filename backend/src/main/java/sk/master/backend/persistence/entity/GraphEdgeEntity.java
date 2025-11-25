package sk.master.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdgeEntity {

    @Column(name = "source_id", nullable = false)
    private long sourceId;

    @Column(name = "target_id", nullable = false)
    private long targetId;

    @Column(nullable = false)
    private double weight;
}