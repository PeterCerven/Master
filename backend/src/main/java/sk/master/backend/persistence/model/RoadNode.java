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
    private int mergeCount; // koľko bodov bolo do tohto nodu zlúčených

    // Metadáta z map matchingu (GraphHopper)
    @Setter
    private String roadName;
    @Setter
    private RoadClass roadClass;
    @Setter
    private double maxSpeed;     // km/h
    @Setter
    private boolean offRoad;     // true ak bod nebol snapnutý na cestu

    // Temporálne metadáta — kedy boli prvý a posledný krát pozorované dáta na tomto uzle
    @Setter
    private Instant firstSeen;
    @Setter
    private Instant lastSeen;

    // H3 cell index pre rýchle priestorové vyhľadávanie
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
     * Zlúči nový bod do tohto nodu cez vážený priemer.
     * Čím viac bodov bolo zlúčených, tým menší vplyv má nový bod.
     */
    public void mergeWith(double newLat, double newLon) {
        this.lat = (this.lat * mergeCount + newLat) / (mergeCount + 1);
        this.lon = (this.lon * mergeCount + newLon) / (mergeCount + 1);
        this.mergeCount++;
    }

    /**
     * Zlúči nový bod vrátane timestamp do tohto nodu.
     * Aktualizuje firstSeen/lastSeen rozsah.
     */
    public void mergeWith(double newLat, double newLon, Instant timestamp) {
        mergeWith(newLat, newLon);
        updateTimestampRange(timestamp);
    }

    /**
     * Rozšíri firstSeen/lastSeen rozsah o daný timestamp.
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
