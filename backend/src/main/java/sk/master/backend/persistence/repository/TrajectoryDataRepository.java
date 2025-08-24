package sk.master.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sk.master.backend.persistence.model.TrajectoryData;

import java.util.Optional;

public interface TrajectoryDataRepository extends JpaRepository<TrajectoryData, Long> {
    Optional<TrajectoryData> findByLatitudeAndLongitudeAndTimestamp(double latitude, double longitude, long timestamp);
}
