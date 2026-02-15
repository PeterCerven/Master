package sk.master.backend.service;

import com.uber.h3core.H3Core;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sk.master.backend.persistence.dto.GraphDto;
import sk.master.backend.persistence.model.PipelineConfig;
import sk.master.backend.persistence.entity.GraphEdgeEntity;
import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.entity.GraphNodeEntity;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadEdge;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.model.RoadNode;
import sk.master.backend.persistence.repository.GraphRepository;
import sk.master.backend.util.GeoUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GpsGraphConstructionService implements GraphConstructionService {
    private static final Logger log = LoggerFactory.getLogger(GpsGraphConstructionService.class);

    private final PipelineConfigService configService;
    private final MapMatchingService mapMatchingService;
    private final H3Core h3;
    private final GraphRepository graphRepository;
    @Getter
    private RoadGraph roadGraph;

    public GpsGraphConstructionService(GraphRepository graphRepository, PipelineConfigService configService, MapMatchingService mapMatchingService) {
        this.graphRepository = graphRepository;
        this.configService = configService;
        this.mapMatchingService = mapMatchingService;
        try {
            this.h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new IllegalStateException("Can't instantiate H3 service", e);
        }
    }

    @Override
    public RoadGraph generateRoadNetwork(GraphDto existingGraph, List<PositionalData> positionalData) {
        // TODO implement incremental graph updates: If existingGraph is provided, only process new points and merge with existing graph instead of rebuilding from scratch.
        if (positionalData == null || positionalData.isEmpty()) {
            log.warn("Empty position list — returning existing graph or new empty one.");
            return new RoadGraph();
        }

        PipelineConfig config = configService.getActivePipelineConfig();
        log.info("=== Pipeline start: {} input points ===", positionalData.size());

        roadGraph = new RoadGraph();

        // Step 1: Preprocessing & Split into Trips
        // Utilizes the tripId from FileServiceImpl
        List<List<PositionalData>> trips = preprocessAndSplitIntoTrips(positionalData, config);
        log.info("Step 1 (preprocessing): Split into {} valid continuous trips", trips.size());

        // Step 2 & 3: Map Matching & Trajectory Insertion
        int processedTrips = 0;
        for (List<PositionalData> trip : trips) {

            // NOTE: You need to implement matchTrajectory in your MapMatchingService
            List<PositionalData> matchedTrajectory = mapMatchingService.matchTrajectory(trip);

            boolean isOffRoad = false;
            List<PositionalData> trajectoryToInsert;

            if (matchedTrajectory != null && matchedTrajectory.size() >= 2) {
                trajectoryToInsert = matchedTrajectory;
            } else {
                // Fallback: If map matching fails, use the raw GPS points (Off-road / Unmapped area)
                // Ideally, apply Douglas-Peucker simplification here to reduce raw GPS jitter.
                isOffRoad = true;
                trajectoryToInsert = trip;
            }

            // Insert strictly chronologically: P1 -> P2 -> P3
            insertTrajectoryIntoGraph(trajectoryToInsert, isOffRoad);
            processedTrips++;
        }
        log.info("Step 2 & 3 (matching & insertion): Processed {} trips. Graph currently has {} nodes, {} edges",
                processedTrips, roadGraph.getNodeCount(), roadGraph.getEdgeCount());

        // Step 4: Spatial Merge & Deduplication via H3
        // Collapses overlapping trajectories from different cars into single road segments
        mergeOverlappingRoadSegments(config);
        log.info("Step 4 (merge & dedup): Graph optimized to {} nodes, {} edges",
                roadGraph.getNodeCount(), roadGraph.getEdgeCount());

        log.info("=== Pipeline completed ===");
        return roadGraph;
    }

    /**
     * Krok 1: Predspracovanie a rozdelenie dát do chronologických trajektórií (jázd).
     */
    private List<List<PositionalData>> preprocessAndSplitIntoTrips(List<PositionalData> positionalData, PipelineConfig config) {
        // 1a) Globálna filtrácia (Coordinate validation + bounding box)
        List<PositionalData> validPoints = new ArrayList<>();
        for (PositionalData p : positionalData) {
            if (isValidCoordinate(p) && isWithinBoundingBox(p, config)) {
                validPoints.add(p);
            }
        }

        // 1b) Zoskupenie do jázd podľa tripId (získané priamo zo súboru)
        Map<Integer, List<PositionalData>> tripsMap = validPoints.stream()
                .collect(Collectors.groupingBy(PositionalData::getTripId));

        List<List<PositionalData>> processedTrips = new ArrayList<>();

        // 1c) Spracovanie každej jazdy samostatne
        for (Map.Entry<Integer, List<PositionalData>> entry : tripsMap.entrySet()) {
            List<PositionalData> trip = entry.getValue();

            // Zotriedenie podľa času
            trip.sort(Comparator.comparing(
                    PositionalData::getTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())
            ));

            // Odstránenie impossible speed jumps v rámci tripu
            trip = removeSpeedOutliersFromTrip(trip, config);

            // Exact duplicate removal
            trip = new ArrayList<>(new LinkedHashSet<>(trip));

            // Výpočet azimutov z konzekutívnych bodov v rámci tripu
            computeBearings(trip);

            // Ak po vyčistení zostal zmysluplný počet bodov pre trajektóriu
            if (trip.size() >= 2) {
                processedTrips.add(trip);
            }
        }

        return processedTrips;
    }

    /**
     * Removes points that imply unrealistic speed relative to the previous point.
     * Assumes the input list is a single, chronologically sorted trip.
     */
    private List<PositionalData> removeSpeedOutliersFromTrip(List<PositionalData> trip, PipelineConfig config) {
        double maxSpeedKmh = config.getMaxSpeedKmh();
        double maxSpeedMs = maxSpeedKmh / 3.6;

        List<PositionalData> result = new ArrayList<>();
        PositionalData prev = null;

        for (PositionalData p : trip) {
            if (prev == null || p.getTimestamp() == null || prev.getTimestamp() == null) {
                result.add(p);
                if (p.getTimestamp() != null) prev = p;
                continue;
            }

            long seconds = Duration.between(prev.getTimestamp(), p.getTimestamp()).getSeconds();

            // Identical timestamps -> keep, but don't compute speed
            if (seconds <= 0) {
                result.add(p);
                continue;
            }

            double dist = GeoUtils.haversineDistance(prev.getLat(), prev.getLon(), p.getLat(), p.getLon());
            double speedMs = dist / seconds;

            if (speedMs <= maxSpeedMs) {
                result.add(p);
                prev = p; // Only update prev if point is valid
            }
        }
        return result;
    }

    /**
     * Inserts a sequential list of points (a trajectory) into the graph chronologically.
     */
    private void insertTrajectoryIntoGraph(List<PositionalData> trajectory, boolean isOffRoad) {
        RoadNode prevNode = null;

        for (PositionalData p : trajectory) {
            RoadNode currentNode = new RoadNode(p.getLat(), p.getLon());
            currentNode.setOffRoad(isOffRoad);

            if (p.getTimestamp() != null) {
                currentNode.updateTimestampRange(p.getTimestamp());
            }

            roadGraph.addNode(currentNode);

            if (prevNode != null) {
                double distance = GeoUtils.haversineDistance(
                        prevNode.getLat(), prevNode.getLon(),
                        currentNode.getLat(), currentNode.getLon()
                );
                roadGraph.addEdge(prevNode, currentNode, distance);
            }

            prevNode = currentNode;
        }
    }

    /**
     * Collapses parallel and overlapping trajectories from different trips
     * into a single unified road network using H3 spatial clustering.
     */
    private void mergeOverlappingRoadSegments(PipelineConfig config) {
        int resolution = config.getH3DedupResolution();

        // Group all nodes by their H3 Hexagon ID
        Map<Long, List<RoadNode>> h3Grid = new HashMap<>();
        for (RoadNode node : roadGraph.getNodes()) {
            long cellId = h3.latLngToCell(node.getLat(), node.getLon(), resolution);
            h3Grid.computeIfAbsent(cellId, _ -> new ArrayList<>()).add(node);
        }

        int mergedNodeCount = 0;

        for (Map.Entry<Long, List<RoadNode>> entry : h3Grid.entrySet()) {
            List<RoadNode> nodesInCell = entry.getValue();
            if (nodesInCell.size() <= 1) continue;

            // Calculate spatial centroid
            double avgLat = nodesInCell.stream().mapToDouble(RoadNode::getLat).average().orElse(0);
            double avgLon = nodesInCell.stream().mapToDouble(RoadNode::getLon).average().orElse(0);

            // Create Master Node
            RoadNode masterNode = new RoadNode(avgLat, avgLon);
            boolean isOffRoad = nodesInCell.stream().allMatch(RoadNode::isOffRoad);
            masterNode.setOffRoad(isOffRoad);

            roadGraph.addNode(masterNode);

            // Rewire all edges touching the old overlapping nodes to the master node
            for (RoadNode oldNode : nodesInCell) {
                Set<RoadEdge> edges = roadGraph.getEdgesOf(oldNode);

                if (edges != null) {
                    for (RoadEdge edge : new ArrayList<>(edges)) {
                        RoadNode otherNode = roadGraph.getNode(
                                edge.sourceId().equals(oldNode.getId()) ? edge.targetId() : edge.sourceId()
                        );

                        if (otherNode != null && !nodesInCell.contains(otherNode)) {
                            double newDist = GeoUtils.haversineDistance(
                                    masterNode.getLat(), masterNode.getLon(),
                                    otherNode.getLat(), otherNode.getLon()
                            );
                            roadGraph.addEdge(masterNode, otherNode, newDist);
                        }
                    }
                }
                roadGraph.removeNode(oldNode);
            }
            mergedNodeCount += (nodesInCell.size() - 1);
        }

        log.info("Merged {} overlapping nodes into unified intersections/road segments.", mergedNodeCount);
    }

    private void computeBearings(List<PositionalData> trip) {
        PositionalData prev = null;
        for (PositionalData p : trip) {
            if (prev != null) {
                double bearing = GeoUtils.initialBearing(
                        prev.getLat(), prev.getLon(), p.getLat(), p.getLon()
                );
                p.setBearing(bearing);
            }
            prev = p;
        }
    }

    private boolean isValidCoordinate(PositionalData p) {
        if (p.getLat() < -90 || p.getLat() > 90 || p.getLon() < -180 || p.getLon() > 180) {
            return false;
        }
        return !(Math.abs(p.getLat()) < 0.001 && Math.abs(p.getLon()) < 0.001);
    }

    private boolean isWithinBoundingBox(PositionalData p, PipelineConfig config) {
        return p.getLat() >= config.getMinLat() && p.getLat() <= config.getMaxLat()
                && p.getLon() >= config.getMinLon() && p.getLon() <= config.getMaxLon();
    }

    @Override
    public GraphEntity saveGraphToDatabase(GraphDto graph, String name) {
        GraphEntity graphEntity = new GraphEntity();
        graphEntity.setName(name);

        graphEntity.setNodes(graph.nodes().stream()
                .map(node -> new GraphNodeEntity(node.id(), node.lat(), node.lon()))
                .collect(Collectors.toList()));

        graphEntity.setEdges(graph.edges().stream()
                .map(edge -> new GraphEdgeEntity(edge.sourceId(), edge.targetId(), edge.distanceMeters()))
                .collect(Collectors.toList()));

        return graphRepository.save(graphEntity);
    }

    @Override
    public GraphDto importGraphFromDatabase(Long graphId) {
        GraphEntity graphEntity = graphRepository.findGraphEntityById(graphId);
        return GraphDto.fromGraphEntity(graphEntity);
    }
}
