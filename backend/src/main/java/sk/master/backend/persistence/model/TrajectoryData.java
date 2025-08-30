package sk.master.backend.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "trajectory_data")
public class TrajectoryData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 9, nullable = false)
    private Double latitude;

    @Column(precision = 9, nullable = false)
    private Double longitude;

    private Instant timestamp;
}
