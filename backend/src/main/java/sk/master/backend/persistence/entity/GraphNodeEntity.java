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
public class GraphNodeEntity {

    @Column(name = "node_id", nullable = false)
    private long nodeId;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;
}