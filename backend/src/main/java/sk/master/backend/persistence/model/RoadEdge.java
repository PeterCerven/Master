package sk.master.backend.persistence.model;

import com.graphhopper.routing.ev.RoadClass;
import lombok.*;


@Getter
public class RoadEdge {

    private final String sourceId;
    private final String targetId;
    @Setter
    private double distanceMeters;

    // Metadáta obohatené map matchingom
    @Setter
    private String roadName;
    @Setter
    private RoadClass roadClass;
    @Setter
    private double maxSpeed;     // km/h

    public RoadEdge(String sourceId, String targetId, double distanceMeters) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.distanceMeters = distanceMeters;
    }

    @Override
    public String toString() {
        return String.format("RoadEdge[%s -> %s, %.1fm, %s]", sourceId, targetId, distanceMeters, roadName);
    }
}