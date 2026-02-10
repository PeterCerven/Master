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

    // Pre-loaded encoded values for fast access
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
     * Snaps a GPS point to the nearest car-accessible road.
     *
     * @return snapped point with metadata, or {@code null} if the point is outside the road network
     */
    @Override
    public SnappedPoint snapToRoad(double lat, double lon) {
        // EdgeFilter — snap only to car-accessible roads (ignore footpaths, cycleways)
        Snap snap = locationIndex.findClosest(lat, lon, edgeState -> edgeState.get(carAccessEnc));

        if (!snap.isValid()) {
            log.debug("Snap failed for point [{}, {}] — too far from road network", lat, lon);
            return null;
        }

        GHPoint3D snappedPoint = snap.getSnappedPoint();
        EdgeIteratorState edge = snap.getClosestEdge();

        return new SnappedPoint(
                snappedPoint.getLat(),
                snappedPoint.getLon(),
                edge.getName(),                    // e.g. "Vajnorská", "D1"
                edge.get(roadClassEnc),            // MOTORWAY, PRIMARY, RESIDENTIAL...
                edge.get(maxSpeedEnc),             // km/h (Double.POSITIVE_INFINITY if not on map)
                edge.get(avgSpeedEnc),             // km/h estimated speed
                edge.getDistance(),                // full edge length in meters
                edge.getEdge()                     // GraphHopper edge ID
        );
    }
}
