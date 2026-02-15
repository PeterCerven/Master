package sk.master.backend.persistence.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

@Setter
@Getter
public class PositionalData {

    private double lat;
    private double lon;
    private Instant timestamp;
    private int tripId;

    public PositionalData(double lat, double lon, Instant timestamp) {
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
    }

    public PositionalData(double lat, double lon, Instant timestamp, int tripId) {
        this(lat, lon, timestamp);
        this.tripId = tripId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PositionalData that = (PositionalData) o;
        return Double.compare(lat, that.lat) == 0 && Double.compare(lon, that.lon) == 0 && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lon, timestamp);
    }
}

