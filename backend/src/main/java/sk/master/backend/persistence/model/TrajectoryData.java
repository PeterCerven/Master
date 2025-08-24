package sk.master.backend.persistence.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class TrajectoryData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Double latitude;
    private Double longitude;
    private Long timestamp;
}
