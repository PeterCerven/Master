package sk.master.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sk.master.backend.persistence.model.TrajectoryData;

import java.time.Instant;
import java.util.Optional;

public interface TrajectoryDataRepository extends JpaRepository<TrajectoryData, Long> {
    Optional<TrajectoryData> findByLatitudeAndLongitudeAndTimestamp(Double latitude, Double longitude, Instant timestamp);
}
