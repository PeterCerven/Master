package sk.master.backend.persistence.model;

import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


public class RoadNode {

    @Getter
    private final String id;
    @Getter
    private double lat;
    @Getter
    private double lon;

    @Setter
    @Getter
    private boolean offRoad;     // true if point was not snapped to a road

    // Temporal metadata — when data was first and last observed at this node
    private Instant firstSeen;
    private Instant lastSeen;


    public RoadNode(double lat, double lon) {
        this.id = UUID.randomUUID().toString();
        this.lat = lat;
        this.lon = lon;
        this.offRoad = false;
    }

    public RoadNode(String id, double lat, double lon) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        this.offRoad = false;
    }

    /**
     * Extends the firstSeen/lastSeen range with the given timestamp.
     */
    public void updateTimestampRange(Instant timestamp) {
        if (timestamp == null) return;
        if (this.firstSeen == null || timestamp.isBefore(this.firstSeen)) {
            this.firstSeen = timestamp;
        }
        if (this.lastSeen == null || timestamp.isAfter(this.lastSeen)) {
            this.lastSeen = timestamp;
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((RoadNode) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("RoadNode[%s, %.6f, %.6f, seen=%s..%s]",
                id, lat, lon, firstSeen, lastSeen);
    }
}
