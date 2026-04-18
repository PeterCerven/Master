package sk.master.backend.service.construct;

import com.uber.h3core.H3Core;
import lombok.Getter;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.scoring.ClusteringCoefficient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.master.backend.persistence.dto.GraphDto;
import sk.master.backend.persistence.dto.GraphMetricsDto;
import sk.master.backend.persistence.dto.GraphSummaryDto;
import sk.master.backend.persistence.dto.PlacementResponseDto;
import sk.master.backend.persistence.dto.SavedGraphDto;
import sk.master.backend.persistence.model.PipelineConfig;
import sk.master.backend.persistence.entity.GraphEdgeEntity;
import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.entity.GraphMetricsEmbeddable;
import sk.master.backend.persistence.entity.GraphNodeEntity;
import sk.master.backend.persistence.entity.GraphStationEntity;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadEdge;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.model.RoadNode;
import sk.master.backend.persistence.repository.GraphRepository;
import sk.master.backend.service.util.PipelineConfigService;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GpsGraphConstructionService implements GraphConstructionService {
    private static final Logger log = LoggerFactory.getLogger(GpsGraphConstructionService.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final PipelineConfigService configService;
    private final MapMatchingService mapMatchingService;
    private final OsmCityGraphService osmCityGraphService;
    private final H3Core h3;
    private final GraphRepository graphRepository;
    @Getter
    private RoadGraph roadGraph;

    public GpsGraphConstructionService(GraphRepository graphRepository, PipelineConfigService configService,
                                       MapMatchingService mapMatchingService, OsmCityGraphService osmCityGraphService) {
        this.graphRepository = graphRepository;
        this.configService = configService;
        this.mapMatchingService = mapMatchingService;
        this.osmCityGraphService = osmCityGraphService;
        try {
            this.h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new IllegalStateException("Can't instantiate H3 service", e);
        }
    }

    @Override
    public RoadGraph generateRoadNetwork(GraphDto existingGraph, List<PositionalData> positionalData) {
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

            List<PositionalData> matchedTrajectory = mapMatchingService.matchTrajectory(trip);

            boolean isOffRoad = false;
            List<PositionalData> trajectoryToInsert;

            if (matchedTrajectory != null && matchedTrajectory.size() >= 2) {
                trajectoryToInsert = matchedTrajectory;
            } else {
                // Fallback: If map matching fails, use the raw GPS points (Off-road / Unmapped area)
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
        // 1a) Globálna filtrácia (Coordinate validation)
        List<PositionalData> validPoints = new ArrayList<>();
        for (PositionalData p : positionalData) {
            if (isValidCoordinate(p)) {
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

            double dist = haversineDistance(prev.getLat(), prev.getLon(), p.getLat(), p.getLon());
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
                double distance = haversineDistance(
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
                            double newDist = haversineDistance(
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

    @Override
    public RoadGraph importCityGraph(String city) {
        PipelineConfig cityConfig = configService.getActivePipelineConfig();
        roadGraph = osmCityGraphService.extractCityGraph(city, cityConfig.getCityCountry(), cityConfig.getRetainLargestComponentPercent(), cityConfig.getCityBoundaryBufferMeters());
        return roadGraph;
    }

    @Override
    public GraphMetricsDto computeMetrics(RoadGraph roadGraph) {
        int nodeCount = roadGraph.getNodeCount();
        int edgeCount = roadGraph.getEdgeCount();

        log.info("Computing graph metrics: nodes={}, edges={}", nodeCount, edgeCount);

        if (nodeCount == 0) {
            return new GraphMetricsDto(0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0);
        }


        double avgDegree = nodeCount > 0 ? 2.0 * edgeCount / nodeCount : 0.0;

        double avgEdgeLengthMeters = roadGraph.getEdges().stream()
                .mapToDouble(RoadEdge::distanceMeters)
                .average()
                .orElse(0.0);

        log.info("Average degree: {}, Average edge length: {} meters", avgDegree, avgEdgeLengthMeters);

        // Node density: bounding box area
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (var node : roadGraph.getNodes()) {
            if (node.getLat() < minLat) minLat = node.getLat();
            if (node.getLat() > maxLat) maxLat = node.getLat();
            if (node.getLon() < minLon) minLon = node.getLon();
            if (node.getLon() > maxLon) maxLon = node.getLon();
        }
        double centerLat = (minLat + maxLat) / 2.0;
        double widthKm = haversineDistance(centerLat, minLon, centerLat, maxLon) / 1000.0;
        double heightKm = haversineDistance(minLat, minLon, maxLat, minLon) / 1000.0;
        double areaKm2 = widthKm * heightKm;
        double nodeDensityPerKm2 = areaKm2 > 0 ? nodeCount / areaKm2 : 0.0;

        log.info("Node density: {} nodes/km² (area: {} km²)", nodeDensityPerKm2, areaKm2);

        // Clustering coefficient via JGraphT
        var cc = new ClusteringCoefficient<>(roadGraph.getGraph());
        double clusteringCoefficient = cc.getAverageClusteringCoefficient();

        log.info("Average clustering coefficient: {}", clusteringCoefficient);

        // Diameter & radius via Dijkstra
        var graph = roadGraph.getGraph();
        List<RoadNode> sources = new ArrayList<>(roadGraph.getNodes());

        boolean connected = new ConnectivityInspector<>(graph).isConnected();

        log.info("Graph connectivity: {}", connected ? "Connected" : "Disconnected. Diameter and radius will be computed on largest component.");

        double diameter = 0.0;
        double radius = Double.MAX_VALUE;

        for (RoadNode source : sources) {
            Map<RoadNode, Double> dist = new HashMap<>();
            dist.put(source, 0.0);
            PriorityQueue<RoadNode> pq = new PriorityQueue<>(Comparator.comparingDouble(n -> dist.getOrDefault(n, Double.MAX_VALUE)));
            pq.add(source);

            while (!pq.isEmpty()) {
                RoadNode u = pq.poll();
                double du = dist.get(u);
                for (RoadEdge edge : graph.edgesOf(u)) {
                    RoadNode src = graph.getEdgeSource(edge);
                    RoadNode v = src.equals(u) ? graph.getEdgeTarget(edge) : src;
                    double dv = du + graph.getEdgeWeight(edge);
                    if (dv < dist.getOrDefault(v, Double.MAX_VALUE)) {
                        dist.put(v, dv);
                        pq.add(v);
                    }
                }
            }

            double eccentricity = 0.0;
            for (Map.Entry<RoadNode, Double> entry : dist.entrySet()) {
                if (!entry.getKey().equals(source)) {
                    double d = entry.getValue();
                    if (d > diameter) diameter = d;
                    if (d > eccentricity) eccentricity = d;
                }
            }
            if (eccentricity > 0 && eccentricity < radius) radius = eccentricity;
        }

        double radiusMeters = radius == Double.MAX_VALUE ? 0.0 : radius;

        log.info("Diameter: {} meters, Radius: {} meters", diameter, radiusMeters);

        var bc = new BetweennessCentrality<>(roadGraph.getGraph(), true);
        double avgBetweennessCentrality = bc.getScores().values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);

        log.info("Average betweenness centrality: {}", avgBetweennessCentrality);

        int treewidth = computeTreewidth(roadGraph);

        log.info("Treewidth: {}", treewidth);


        return new GraphMetricsDto(
                nodeCount,
                edgeCount,
                avgDegree,
                diameter,
                clusteringCoefficient,
                avgEdgeLengthMeters,
                nodeDensityPerKm2,
                connected,
                radiusMeters,
                avgBetweennessCentrality,
                treewidth
        );
    }

    @SuppressWarnings("unchecked")
    private int computeTreewidth(RoadGraph roadGraph) {
        List<RoadNode> nodes = new ArrayList<>(roadGraph.getNodes());
        int n = nodes.size();
        if (n == 0) return 0;

        Map<RoadNode, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.put(nodes.get(i), i);

        Set<Integer>[] adj = new Set[n];
        for (int i = 0; i < n; i++) adj[i] = new HashSet<>();
        var graph = roadGraph.getGraph();
        for (RoadEdge e : roadGraph.getEdges()) {
            int u = idx.get(graph.getEdgeSource(e));
            int v = idx.get(graph.getEdgeTarget(e));
            if (u != v) { adj[u].add(v); adj[v].add(u); }
        }

        boolean[] eliminated = new boolean[n];
        int treewidth = 0;
        for (int step = 0; step < n; step++) {
            int minDeg = Integer.MAX_VALUE, minV = -1;
            for (int i = 0; i < n; i++) {
                if (!eliminated[i] && adj[i].size() < minDeg) {
                    minDeg = adj[i].size();
                    minV = i;
                }
            }
            treewidth = Math.max(treewidth, minDeg);
            List<Integer> nbrs = new ArrayList<>(adj[minV]);
            for (int i = 0; i < nbrs.size(); i++) {
                for (int j = i + 1; j < nbrs.size(); j++) {
                    adj[nbrs.get(i)].add(nbrs.get(j));
                    adj[nbrs.get(j)].add(nbrs.get(i));
                }
            }
            for (int nb : nbrs) adj[nb].remove(minV);
            adj[minV].clear();
            eliminated[minV] = true;
        }
        return treewidth;
    }

    private boolean isValidCoordinate(PositionalData p) {
        if (p.getLat() < -90 || p.getLat() > 90 || p.getLon() < -180 || p.getLon() > 180) {
            return false;
        }
        return !(Math.abs(p.getLat()) < 0.001 && Math.abs(p.getLon()) < 0.001);
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    @Override
    public GraphMetricsDto computeCurrentGraphMetrics() {
        if (roadGraph == null) return null;
        return computeMetrics(roadGraph);
    }

    @Override
    @Transactional
    public GraphSummaryDto saveGraphToDatabase(GraphDto graph, List<PlacementResponseDto.StationNodeDto> stations, String name, Long userId) {
        GraphEntity graphEntity = new GraphEntity();
        graphEntity.setName(name);
        graphEntity.setUserId(userId);

        graphEntity.setNodes(graph.nodes().stream()
                .map(node -> new GraphNodeEntity(node.id(), node.lat(), node.lon()))
                .collect(Collectors.toList()));

        graphEntity.setEdges(graph.edges().stream()
                .map(edge -> new GraphEdgeEntity(edge.sourceId(), edge.targetId(), edge.distanceMeters()))
                .collect(Collectors.toList()));

        if (stations != null) {
            graphEntity.setStations(stations.stream()
                    .map(s -> new GraphStationEntity(s.id(), s.lat(), s.lon(), s.rank()))
                    .collect(Collectors.toList()));
        }

        if (graph.metrics() != null) {
            graphEntity.setMetrics(GraphMetricsEmbeddable.fromDto(graph.metrics()));
        }

        GraphEntity saved = graphRepository.save(graphEntity);
        return GraphSummaryDto.fromEntity(saved);
    }

    @Override
    public SavedGraphDto importGraphFromDatabase(Long graphId, Long userId) {
        GraphEntity graphEntity = graphRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found: " + graphId));
        return SavedGraphDto.fromEntity(graphEntity);
    }

    @Override
    public List<GraphSummaryDto> listUserGraphs(Long userId) {
        return graphRepository.findSummariesByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteGraph(Long graphId, Long userId) {
        GraphEntity entity = graphRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found"));
        graphRepository.delete(entity);
    }

    @Override
    @Transactional
    public GraphSummaryDto renameGraph(Long graphId, String newName, Long userId) {
        GraphEntity entity = graphRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found"));
        entity.setName(newName);
        return GraphSummaryDto.fromEntity(graphRepository.save(entity));
    }
}
