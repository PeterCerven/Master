package sk.master.backend.persistence.model;

import com.graphhopper.routing.ev.RoadClass;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public class RoadNode {

    private final String id;
    @Setter
    private double lat;
    @Setter
    private double lon;
    private int mergeCount; // how many points were merged into this node

    // Metadata from map matching (GraphHopper)
    @Setter
    private String roadName;
    @Setter
    private RoadClass roadClass;
    @Setter
    private double maxSpeed;     // km/h
    @Setter
    private boolean offRoad;     // true if point was not snapped to a road

    // Temporal metadata — when data was first and last observed at this node
    @Setter
    private Instant firstSeen;
    @Setter
    private Instant lastSeen;

    // H3 cell index for fast spatial lookups
    @Setter
    private long h3CellId;

    public RoadNode(double lat, double lon) {
        this.id = UUID.randomUUID().toString();
        this.lat = lat;
        this.lon = lon;
        this.mergeCount = 1;
        this.offRoad = false;
    }

    /**
     * Merges a new point into this node via weighted average.
     * The more points merged, the less impact a new point has.
     */
    public void mergeWith(double newLat, double newLon) {
        this.lat = (this.lat * mergeCount + newLat) / (mergeCount + 1);
        this.lon = (this.lon * mergeCount + newLon) / (mergeCount + 1);
        this.mergeCount++;
    }

    /**
     * Merges a new point including timestamp into this node.
     * Updates the firstSeen/lastSeen range.
     */
    public void mergeWith(double newLat, double newLon, Instant timestamp) {
        mergeWith(newLat, newLon);
        updateTimestampRange(timestamp);
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
        return String.format("RoadNode[%s, %.6f, %.6f, road=%s, seen=%s..%s]",
                id, lat, lon, roadName, firstSeen, lastSeen);
    }
}
