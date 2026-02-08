package sk.master.backend.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sk.master.backend.persistence.model.SnappedPoint;

@Service
public class MapMatchingServiceGraphHopper implements MapMatchingService {

    private static final Logger log = LoggerFactory.getLogger(MapMatchingServiceGraphHopper.class);

    private final LocationIndex locationIndex;
    private final EncodingManager encodingManager;

    // Prednačítané encoded values pre rýchly prístup
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final DecimalEncodedValue maxSpeedEnc;
    private final DecimalEncodedValue avgSpeedEnc;
    private final BooleanEncodedValue carAccessEnc;

    public MapMatchingServiceGraphHopper(GraphHopper hopper) {
        this.locationIndex = hopper.getLocationIndex();
        this.encodingManager = hopper.getEncodingManager();

        this.roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        this.maxSpeedEnc = encodingManager.getDecimalEncodedValue(MaxSpeed.KEY);
        this.avgSpeedEnc = encodingManager.getDecimalEncodedValue("car_average_speed");
        this.carAccessEnc = encodingManager.getBooleanEncodedValue("car_access");
    }

    /**
     * Snapne GPS bod na najbližšiu cestu prístupnú autom.
     *
     * @return snapnutý bod s metadátami, alebo {@code null} ak bod je mimo cestnej siete
     */
    @Override
    public SnappedPoint snapToRoad(double lat, double lon) {
        // EdgeFilter — snapuj len na cesty prístupné autom (ignoruj chodníky, cyklotrasy)
        Snap snap = locationIndex.findClosest(lat, lon, edgeState -> edgeState.get(carAccessEnc));

        if (!snap.isValid()) {
            log.debug("Snap neúspešný pre bod [{}, {}] — príliš ďaleko od cestnej siete", lat, lon);
            return null;
        }

        GHPoint3D snappedPoint = snap.getSnappedPoint();
        EdgeIteratorState edge = snap.getClosestEdge();

        return new SnappedPoint(
                snappedPoint.getLat(),
                snappedPoint.getLon(),
                edge.getName(),                    // napr. "Vajnorská", "D1"
                edge.get(roadClassEnc),            // MOTORWAY, PRIMARY, RESIDENTIAL...
                edge.get(maxSpeedEnc),             // km/h (Double.POSITIVE_INFINITY ak nie je na mape)
                edge.get(avgSpeedEnc),             // km/h odhadnutá rýchlosť
                edge.getDistance(),                // dĺžka celej hrany v metroch
                edge.getEdge()                     // GraphHopper edge ID
        );
    }
}
