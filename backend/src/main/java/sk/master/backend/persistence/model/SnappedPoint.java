package sk.master.backend.persistence.model;

import com.graphhopper.routing.ev.RoadClass;
import org.jetbrains.annotations.NotNull;

/**
 * @param edgeDistance full edge length in meters
 * @param edgeId       GraphHopper edge ID (for same-segment detection)
 */
public record SnappedPoint(double lat, double lon, String roadName, RoadClass roadClass, double maxSpeed,
                           double avgSpeed, double edgeDistance, int edgeId) {

    @NotNull
    @Override
    public String toString() {
        return String.format("SnappedPoint[%.6f, %.6f, %s, %s, %.0f km/h]",
                lat, lon, roadName, roadClass, maxSpeed);
    }
}
