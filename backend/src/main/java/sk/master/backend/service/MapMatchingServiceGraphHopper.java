package sk.master.backend.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;

import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sk.master.backend.persistence.model.PositionalData;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MapMatchingServiceGraphHopper implements MapMatchingService {

    private static final Logger log = LoggerFactory.getLogger(MapMatchingServiceGraphHopper.class);
    private final GraphHopper hopper;

    public MapMatchingServiceGraphHopper(GraphHopper hopper) {
            this.hopper = hopper;
    }

    @Override
    public List<PositionalData> matchTrajectory(List<PositionalData> trip) {
        if (trip == null || trip.size() < 2) return null;

        // 1. Convert our PositionalData to GraphHopper Observations
        List<Observation> observations = trip.stream()
                .map(p -> new Observation(new GHPoint(p.getLat(), p.getLon())))
                .collect(Collectors.toList());

        // 2. Initialize MapMatching per request (MapMatching is NOT thread-safe, so we instantiate it here)
        // Ensure "car" matches the profile name you configured when starting GraphHopper
        PMap hints = new PMap().putObject("profile", "car");
        MapMatching mapMatching = MapMatching.fromGraphHopper(hopper, hints);

        try {
            // 3. Perform the HMM map matching
            MatchResult matchResult = mapMatching.match(observations);

            // 4. Extract the continuous, clean road geometry
            PointList matchedPoints = matchResult.getMergedPath().calcPoints();
            List<PositionalData> matchedTrajectory = new ArrayList<>();
            int tripId = trip.getFirst().getTripId(); // Preserve the trip ID

            for (int i = 0; i < matchedPoints.size(); i++) {
                // Note: GraphHopper's merged path drops timestamps. We leave them null.
                // For building a static road network graph, physical geometry is what matters.
                matchedTrajectory.add(new PositionalData(
                        matchedPoints.getLat(i),
                        matchedPoints.getLon(i),
                        null,
                        tripId
                ));
            }

            return matchedTrajectory;

        } catch (Exception e) {
            // Matching can fail if the GPS track is completely off-road or too noisy
            log.debug("HMM Map matching failed for trip {}: {}", trip.getFirst().getTripId(), e.getMessage());
            return null; // Our GpsGraphConstructionService will gracefully fallback to raw points when this returns null
        }
    }
}
