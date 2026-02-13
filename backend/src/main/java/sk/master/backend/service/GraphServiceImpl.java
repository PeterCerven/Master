package sk.master.backend.service;

import com.uber.h3core.H3Core;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sk.master.backend.config.PipelineConfig;
import sk.master.backend.persistence.entity.GraphEdgeEntity;
import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.entity.GraphNodeEntity;
import sk.master.backend.persistence.model.*;
import sk.master.backend.persistence.repository.GraphRepository;
import sk.master.backend.util.GeoUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GraphServiceImpl implements GraphService {
    private static final Logger log = LoggerFactory.getLogger(GraphServiceImpl.class);

    private final PipelineConfigService configService;
    private final MapMatchingService mapMatchingService;
    private final H3Core h3;
    private final GraphRepository graphRepository;

    public GraphServiceImpl(GraphRepository graphRepository, PipelineConfigService configService, MapMatchingServiceGraphHopper mapMatchingService) {
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
    public RoadGraph generateRoadNetwork(RoadGraph existingGraph, List<PositionalData> positionalData) {
        if (positionalData == null || positionalData.isEmpty()) {
            log.warn("Empty position list — returning existing graph or new empty one.");
            return existingGraph != null ? existingGraph : new RoadGraph();
        }

        PipelineConfig config = configService.getActivePipelineConfig();
        log.info("=== Pipeline start: {} input points ===", positionalData.size());

        // Krok 1: Predspracovanie
        List<PositionalData> filtered = preprocess(positionalData, config);
        log.info("Step 1 (preprocessing): {} → {} points", positionalData.size(), filtered.size());

        if (filtered.isEmpty()) {
            log.warn("All points were filtered out — returning existing graph or empty one.");
            return existingGraph != null ? existingGraph : new RoadGraph();
        }

        // Step 2: H3 Spatial Index + deduplication
        List<PositionalData> indexed = spatialIndexAndDedup(filtered, config);
        log.info("Step 2 (H3 dedup): {} → {} points", filtered.size(), indexed.size());

        // Step 3: Incremental graph insertion
        RoadGraph graph = existingGraph != null ? existingGraph : new RoadGraph();
        buildGraph(graph, indexed, config);
        log.info("Step 3 (graph): {} nodes, {} edges", graph.getNodeCount(), graph.getEdgeCount());

        // Krok 4: Map Matching (GraphHopper)
        applyMapMatching(graph, config);
        log.info("Step 4 (map matching): {} nodes, {} edges (after correction)",
                graph.getNodeCount(), graph.getEdgeCount());

        log.info("=== Pipeline completed ===");
        return graph;
    }

    private List<PositionalData> preprocess(List<PositionalData> positionalData, PipelineConfig config) {
        List<PositionalData> result = new ArrayList<>();

        // 1a) Coordinate validation + bounding box
        for (PositionalData p : positionalData) {
            if (isValidCoordinate(p) && isWithinBoundingBox(p, config)) {
                result.add(p);
            }
        }
        log.debug("  After validation + bounding box: {}", result.size());

        // 1b) Sort by timestamp (points without timestamp go to end)
        result.sort(Comparator.comparing(
                PositionalData::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        log.debug("  After sorting by timestamp");

        // 1c) Speed-based outlier removal — points with unrealistic speed relative to previous
        result = removeSpeedOutliers(result, config);
        log.debug("  After speed filter: {}", result.size());

        // 1d) Exact duplicate removal (via HashSet — uses PositionalData.equals/hashCode)
        result = new ArrayList<>(new LinkedHashSet<>(result));
        log.debug("  After exact dedup: {}", result.size());

        // 1e) Near-duplicate removal
        result = removeNearDuplicates(result, config);
        log.debug("  After near-dedup: {}", result.size());

        // 1f) Density-based outlier removal
        result = removeOutliers(result, config);
        log.debug("  After outlier removal: {}", result.size());

        return result;
    }

    /**
     * Validates that coordinates are valid and not null-island (0,0).
     */
    private boolean isValidCoordinate(PositionalData p) {
        if (p.getLat() < -90 || p.getLat() > 90 || p.getLon() < -180 || p.getLon() > 180) {
            return false;
        }
        // Null island check — (0,0) is in the Gulf of Guinea, definitely not in Slovakia
        return !(Math.abs(p.getLat()) < 0.001 && Math.abs(p.getLon()) < 0.001);
    }

    /**
     * Checks whether the point lies within the configured bounding box (default: Slovakia).
     */
    private boolean isWithinBoundingBox(PositionalData p, PipelineConfig config) {
        return p.getLat() >= config.getMinLat() && p.getLat() <= config.getMaxLat()
                && p.getLon() >= config.getMinLon() && p.getLon() <= config.getMaxLon();
    }

    /**
     * Removes points that imply unrealistic speed relative to the previous point.
     * Assumes the input list is sorted by timestamp.
     * Points without timestamp are kept (speed cannot be calculated).
     * Speed is calculated only between consecutive points within the same trip
     * (gap > tripGapMinutes means a new trip).
     */
    private List<PositionalData> removeSpeedOutliers(List<PositionalData> positionalData, PipelineConfig config) {
        double maxSpeedKmh = config.getMaxSpeedKmh();
        long tripGapMinutes = config.getTripGapMinutes();
        double maxSpeedMs = maxSpeedKmh / 3.6; // km/h → m/s

        List<PositionalData> result = new ArrayList<>();
        PositionalData prev = null;
        int removed = 0;

        for (PositionalData p : positionalData) {
            if (prev == null || p.getTimestamp() == null || prev.getTimestamp() == null) {
                // First point or points without timestamp — always keep
                result.add(p);
                if (p.getTimestamp() != null) {
                    prev = p;
                }
                continue;
            }

            Duration timeDiff = Duration.between(prev.getTimestamp(), p.getTimestamp());
            long seconds = timeDiff.getSeconds();

            // If gap is larger than trip gap, it's a new trip — reset
            if (seconds > tripGapMinutes * 60) {
                result.add(p);
                prev = p;
                continue;
            }

            // If time difference is zero or negative, keep the point (same time)
            if (seconds <= 0) {
                result.add(p);
                continue;
            }

            double dist = GeoUtils.haversineDistance(
                    prev.getLat(), prev.getLon(), p.getLat(), p.getLon()
            );
            double speedMs = dist / seconds;

            if (speedMs <= maxSpeedMs) {
                result.add(p);
                prev = p;
            } else {
                removed++;
                // Don't update prev — skipped point shouldn't affect further comparisons
            }
        }

        if (removed > 0) {
            log.debug("  Speed filter: removed {} points (>{} km/h)", removed, maxSpeedKmh);
        }
        return result;
    }

    /**
     * Removes near-duplicate points — points closer than threshold are merged.
     * Uses grid-based spatial hashing for O(n) average complexity.
     */
    private List<PositionalData>
    removeNearDuplicates(List<PositionalData> positionalData, PipelineConfig config) {
        double threshold = config.getNearDuplicateThresholdM();
        // Grid cell size ~ threshold in degrees
        double cellSize = threshold / 111_320.0;

        Map<String, PositionalData> gridMap = new LinkedHashMap<>();

        for (PositionalData p : positionalData) {
            String cellKey = (int) (p.getLat() / cellSize) + ":" + (int) (p.getLon() / cellSize);
            if (!gridMap.containsKey(cellKey)) {
                gridMap.put(cellKey, p);
            }
            // If cell is already occupied, check distance
            // If close, skip; otherwise use unique key
            else {
                PositionalData existing = gridMap.get(cellKey);
                double dist = GeoUtils.equirectangularDistance(
                        p.getLat(), p.getLon(), existing.getLat(), existing.getLon()
                );
                if (dist >= threshold) {
                    // In the same cell but far enough apart — use composite key
                    String uniqueKey = cellKey + ":" + gridMap.size();
                    gridMap.put(uniqueKey, p);
                }
                // else: near-duplicate, skip
            }
        }
        return new ArrayList<>(gridMap.values());
    }

    /**
     * Removes isolated points (outliers) — points with fewer than minNeighbors
     * within outlierRadius are considered noise.
     *
     * <p>Uses a temporary {@link Quadtree} for O(n log n) range queries
     * instead of O(n²) brute force. Quadtree supports dynamic insert,
     * so the same pattern works for incremental scenarios too.
     */
    private List<PositionalData> removeOutliers(List<PositionalData> positionalData, PipelineConfig config) {
        int minNeighbors = config.getOutlierMinNeighbors();
        double radius = config.getOutlierRadiusM();

        // 1) Insert all points into temporary Quadtree
        Quadtree tree = new Quadtree();
        for (PositionalData p : positionalData) {
            Envelope env = new Envelope(
                    new Coordinate(p.getLon(), p.getLat())
            );
            tree.insert(env, p);
        }

        // 2) For each point do a range query instead of iterating over all points
        double degreeApprox = radius / 111_320.0;
        List<PositionalData> result = new ArrayList<>();

        for (PositionalData p : positionalData) {
            Envelope searchEnv = new Envelope(
                    p.getLon() - degreeApprox, p.getLon() + degreeApprox,
                    p.getLat() - degreeApprox, p.getLat() + degreeApprox
            );

            @SuppressWarnings("unchecked")
            List<PositionalData> candidates = tree.query(searchEnv);

            // Refinement: rectangle → circle (check actual distance)
            int neighborCount = 0;
            for (PositionalData candidate : candidates) {
                if (candidate == p) continue;
                double dist = GeoUtils.equirectangularDistance(
                        p.getLat(), p.getLon(), candidate.getLat(), candidate.getLon()
                );
                if (dist <= radius) {
                    neighborCount++;
                    if (neighborCount >= minNeighbors) break; // early exit
                }
            }

            if (neighborCount >= minNeighbors) {
                result.add(p);
            }
        }
        return result;
    }

    // =========================================================================
    // STEP 2: H3 SPATIAL INDEX + DEDUPLICATION
    // =========================================================================

    /**
     * Indexes points into H3 hexagonal cells and deduplicates —
     * points in the same resolution-11 cell (~29m) are merged to centroid.
     * Preserves earliest and latest timestamps from points in the cell.
     */
    private List<PositionalData> spatialIndexAndDedup(List<PositionalData> positionalData, PipelineConfig config) {
        int resolution = config.getH3DedupResolution();

        // Group points by H3 cell ID
        Map<Long, List<PositionalData>> cellMap = new HashMap<>();
        for (PositionalData p : positionalData) {
            long cellId = h3.latLngToCell(p.getLat(), p.getLon(), resolution);
            cellMap.computeIfAbsent(cellId, _ -> new ArrayList<>()).add(p);
        }

        // For each cell, return centroid of all points in the cell with earliest timestamp
        List<PositionalData> deduplicated = new ArrayList<>();
        for (Map.Entry<Long, List<PositionalData>> entry : cellMap.entrySet()) {
            List<PositionalData> cellPoints = entry.getValue();
            double avgLat = cellPoints.stream().mapToDouble(PositionalData::getLat).average().orElse(0);
            double avgLon = cellPoints.stream().mapToDouble(PositionalData::getLon).average().orElse(0);

            // Keep earliest timestamp from points in the cell (representative time)
            Instant earliest = cellPoints.stream()
                    .map(PositionalData::getTimestamp)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);

            deduplicated.add(new PositionalData(avgLat, avgLon, earliest));
        }

        return deduplicated;
    }

    // =========================================================================
    // STEP 3: INCREMENTAL GRAPH INSERTION
    // =========================================================================

    /**
     * Inserts points into the graph — either merge into existing nodes or create new ones.
     * For a new graph, first runs DBSCAN clustering and Delaunay triangulation.
     * For an existing graph, uses incremental merge-or-create pattern.
     */
    private void buildGraph(RoadGraph graph, List<PositionalData> positionalData, PipelineConfig config) {
        if (graph.getNodeCount() == 0) {
            // New graph — run full pipeline: DBSCAN → Delaunay → Gabriel pruning
            buildNewGraph(graph, positionalData, config);
        } else {
            // Existing graph — incremental insertion
            incrementalInsert(graph, positionalData, config);
        }
    }

    /**
     * Builds a new graph from scratch using DBSCAN + Delaunay + Gabriel pruning.
     */
    private void buildNewGraph(RoadGraph graph, List<PositionalData> graphPoints, PipelineConfig config) {
        // 3a) Použijem všetky H3-deduplikované body priamo ako uzly grafu.
        // DBSCAN centroidy strácajú priestorovú kontinuitu — Delaunay + Gabriel
        // pruning lepšie zachovajú topológiu cestnej siete.
        log.debug("  Using all {} dedup points as nodes (DBSCAN bypassed)", graphPoints.size());

        // 3b) Vytvor nody z bodov (vrátane temporálnych metadát)
        List<RoadNode> nodes = new ArrayList<>();
        for (PositionalData p : graphPoints) {
            RoadNode node = new RoadNode(p.getLat(), p.getLon());
            node.setH3CellId(h3.latLngToCell(p.getLat(), p.getLon(), config.getH3ClusterResolution()));
            node.updateTimestampRange(p.getTimestamp());
            graph.addNode(node);
            nodes.add(node);
        }

        // 3c) Delaunay triangulation → vytvori hrany
        connectWithDelaunay(graph, nodes);

        // 3d) Gabriel graph pruning — odstráni spurious cross-connections
        pruneToGabrielGraph(graph);

        // 3e) Odstráni hrany dlhšie než maxEdgeLength
        pruneByMaxLength(graph, config);
    }

    /**
     * Spojí nody pomocou Delaunay triangulácie (JTS).
     */
    private void connectWithDelaunay(RoadGraph graph, List<RoadNode> nodes) {
        if (nodes.size() < 2) return;

        GeometryFactory gf = new GeometryFactory();
        DelaunayTriangulationBuilder builder = new DelaunayTriangulationBuilder();

        // Vytvor JTS Coordinate array — pozor: Coordinate(x=lon, y=lat)
        List<Coordinate> coords = nodes.stream()
                .map(n -> new Coordinate(n.getLon(), n.getLat()))
                .collect(Collectors.toList());

        builder.setSites(coords);

        Geometry triangles = builder.getTriangles(gf);

        // Prechádzaj trojuholníky a vytváraj hrany
        Map<String, RoadNode> coordToNode = new HashMap<>();
        for (RoadNode n : nodes) {
            String key = coordKey(n.getLon(), n.getLat());
            coordToNode.put(key, n);
        }

        for (int i = 0; i < triangles.getNumGeometries(); i++) {
            Geometry triangle = triangles.getGeometryN(i);
            Coordinate[] triCoords = triangle.getCoordinates();

            // Trojuholník má 4 body (posledný = prvý), čiže 3 hrany
            for (int j = 0; j < 3; j++) {
                Coordinate c1 = triCoords[j];
                Coordinate c2 = triCoords[(j + 1) % 3];

                RoadNode n1 = coordToNode.get(coordKey(c1.x, c1.y));
                RoadNode n2 = coordToNode.get(coordKey(c2.x, c2.y));

                if (n1 != null && n2 != null && !n1.equals(n2)) {
                    double dist = GeoUtils.haversineDistance(n1.getLat(), n1.getLon(), n2.getLat(), n2.getLon());
                    graph.addEdge(n1, n2, dist);
                }
            }
        }
    }

    /**
     * Gabriel graph pruning — hrana p-q prežije len ak žiadny iný bod neleží
     * v kruhu s priemerom p-q (midpoint + radius = dist/2).
     *
     * <p>Používa priamo {@code graph.findInRadius()} — nody sú už v grafovom
     * Quadtree, nie je potrebné budovať žiadny dočasný index.
     * Pre každú hranu vykoná radius query okolo midpointu,
     * čím sa testujú len lokálne nody (typicky 2-10) namiesto celého datasetu.
     *
     * <p>Princíp Gabriel grafu: MST ⊆ RNG ⊆ Gabriel ⊆ Delaunay.
     * Gabriel je optimálna hustota pre cestné siete — dostatočne spojitý,
     * ale bez spurious krížových hrán.
     */
    private void pruneToGabrielGraph(RoadGraph graph) {
        List<RoadEdge> toRemove = new ArrayList<>();

        for (RoadEdge edge : graph.getEdges()) {
            RoadNode source = graph.getNode(edge.getSourceId());
            RoadNode target = graph.getNode(edge.getTargetId());
            if (source == null || target == null) continue;

            double midLat = (source.getLat() + target.getLat()) / 2;
            double midLon = (source.getLon() + target.getLon()) / 2;
            double radiusMeters = edge.getDistanceMeters() / 2;

            // Radius query cez grafový Quadtree — vráti len lokálne nody
            // Exclude source a target, aby sa netestovali samy voči sebe
            Set<RoadNode> exclude = Set.of(source, target);
            List<RoadNode> violators = graph.findInRadius(midLat, midLon, radiusMeters, exclude);

            if (!violators.isEmpty()) {
                toRemove.add(edge);
            }
        }

        // Odstráň porušujúce hrany
        for (RoadEdge edge : toRemove) {
            RoadNode source = graph.getNode(edge.getSourceId());
            RoadNode target = graph.getNode(edge.getTargetId());
            if (source != null && target != null) {
                graph.getJGraphTGraph().removeEdge(source, target);
            }
        }
        log.debug("  Gabriel pruning: removed {} edges (from {} tested)",
                toRemove.size(), graph.getEdgeCount() + toRemove.size());
    }

    /**
     * Odstráni hrany dlhšie než maxEdgeLength.
     */
    private void pruneByMaxLength(RoadGraph graph, PipelineConfig config) {
        double maxLen = config.getMaxEdgeLengthM();
        List<RoadEdge> toRemove = graph.getEdges().stream()
                .filter(e -> e.getDistanceMeters() > maxLen)
                .toList();

        for (RoadEdge edge : toRemove) {
            RoadNode source = graph.getNode(edge.getSourceId());
            RoadNode target = graph.getNode(edge.getTargetId());
            if (source != null && target != null) {
                graph.getJGraphTGraph().removeEdge(source, target);
            }
        }
        log.debug("  Max-length pruning: removed {} edges (>{} m)", toRemove.size(), maxLen);
    }

    /**
     * Inkrementálne vloží nové body do existujúceho grafu.
     * Pattern: merge-or-create + KNN spojenie + lokálny Gabriel pruning.
     */
    private void incrementalInsert(RoadGraph graph, List<PositionalData> positionalData, PipelineConfig config) {
        double mergeThreshold = config.getMergeThresholdM();
        double maxEdgeLen = config.getMaxEdgeLengthM();
        int k = config.getKnnK();

        int merged = 0;
        int created = 0;

        for (PositionalData p : positionalData) {
            // Skús nájsť existujúci node v rámci merge threshold
            RoadNode nearest = graph.findNearest(p.getLat(), p.getLon(), mergeThreshold);

            if (nearest != null) {
                // Merge — aktualizuj pozíciu cez vážený priemer + rozšír timestamp rozsah
                nearest.mergeWith(p.getLat(), p.getLon(), p.getTimestamp());
                merged++;
            } else {
                // Create — nový node + spoj s K najbližšími susedmi
                RoadNode newNode = new RoadNode(p.getLat(), p.getLon());
                newNode.setH3CellId(h3.latLngToCell(p.getLat(), p.getLon(), config.getH3ClusterResolution()));
                newNode.updateTimestampRange(p.getTimestamp());
                graph.addNode(newNode);

                // KNN — spoj s najbližšími existujúcimi nodmi
                List<RoadNode> neighbors = graph.findKNearest(
                        p.getLat(), p.getLon(), k, maxEdgeLen
                );

                List<RoadEdge> newEdges = new ArrayList<>();
                for (RoadNode neighbor : neighbors) {
                    if (neighbor.equals(newNode)) continue;
                    double dist = GeoUtils.haversineDistance(
                            newNode.getLat(), newNode.getLon(),
                            neighbor.getLat(), neighbor.getLon()
                    );
                    if (dist <= maxEdgeLen) {
                        graph.addEdge(newNode, neighbor, dist);
                        RoadEdge edge = graph.getJGraphTGraph().getEdge(newNode, neighbor);
                        if (edge != null) newEdges.add(edge);
                    }
                }

                // Lokálny Gabriel pruning — odstráni spurious cross-connections
                pruneEdgesGabriel(graph, newEdges);
                created++;
            }
        }
        log.debug("  Incremental insert: {} merged, {} created", merged, created);
    }

    /**
     * Gabriel pruning na zadanej podmnožine hrán.
     * Hrana prežije len ak žiadny iný node neleží v diametrálnom kruhu.
     */
    private void pruneEdgesGabriel(RoadGraph graph, List<RoadEdge> edges) {
        List<RoadEdge> toRemove = new ArrayList<>();

        for (RoadEdge edge : edges) {
            RoadNode source = graph.getNode(edge.getSourceId());
            RoadNode target = graph.getNode(edge.getTargetId());
            if (source == null || target == null) continue;

            double midLat = (source.getLat() + target.getLat()) / 2;
            double midLon = (source.getLon() + target.getLon()) / 2;
            double radiusMeters = edge.getDistanceMeters() / 2;

            Set<RoadNode> exclude = Set.of(source, target);
            List<RoadNode> violators = graph.findInRadius(midLat, midLon, radiusMeters, exclude);

            if (!violators.isEmpty()) {
                toRemove.add(edge);
            }
        }

        for (RoadEdge edge : toRemove) {
            RoadNode source = graph.getNode(edge.getSourceId());
            RoadNode target = graph.getNode(edge.getTargetId());
            if (source != null && target != null) {
                graph.getJGraphTGraph().removeEdge(source, target);
            }
        }
    }

    // =========================================================================
    // KROK 4: MAP MATCHING (GRAPHHOPPER)
    // =========================================================================

    /**
     * Koriguje pozície nodov pomocou GraphHopper snap-to-road
     * a obohacuje nody/hrany o cestné metadáta.
     */
    private void applyMapMatching(RoadGraph graph, PipelineConfig config) {
        double maxSnapDist = config.getMaxSnapDistanceM();
        int snapped = 0;
        int offRoad = 0;

        // Map pre detekciu nodov na rovnakom road segmente
        Map<Integer, List<RoadNode>> edgeIdToNodes = new HashMap<>();

        for (RoadNode node : graph.getNodes()) {
            SnappedPoint sp = mapMatchingService.snapToRoad(node.getLat(), node.getLon());

            if (sp == null) {
                node.setOffRoad(true);
                offRoad++;
                continue;
            }

            double snapDistance = GeoUtils.haversineDistance(
                    node.getLat(), node.getLon(), sp.lat(), sp.lon()
            );

            if (snapDistance <= maxSnapDist) {
                // Koriguj pozíciu na snapnuté súradnice
                node.setLat(sp.lat());
                node.setLon(sp.lon());
                node.setRoadName(sp.roadName());
                node.setRoadClass(sp.roadClass());
                node.setMaxSpeed(sp.maxSpeed());
                snapped++;

                // Zaznamenaj, na ktorý edge bol snapnutý
                edgeIdToNodes.computeIfAbsent(sp.edgeId(), _ -> new ArrayList<>()).add(node);
            } else {
                node.setOffRoad(true);
                offRoad++;
            }
        }

        log.debug("  Map matching: {} snapped, {} off-road", snapped, offRoad);

        // Obohať hrany grafu o metadáta
        enrichEdgeMetadata(graph);

        // Zlúč nody, ktoré boli snapnuté na rovnaký GraphHopper edge (len ak sú blízke)
        mergeNodesOnSameEdge(graph, edgeIdToNodes, config.getMergeThresholdM());

        // Prepočítaj edge weights na reálne vzdialenosti (po snap korekcii)
        recalculateEdgeWeights(graph);

        // Voliteľne: odstráň off-road nody
        if (config.isRemoveOffRoadNodes()) {
            removeOffRoadNodes(graph);
        }
    }

    /**
     * Obohatí hrany grafu o cestné metadáta z ich koncových nodov.
     */
    private void enrichEdgeMetadata(RoadGraph graph) {
        for (RoadEdge edge : graph.getEdges()) {
            RoadNode source = graph.getNode(edge.getSourceId());
            RoadNode target = graph.getNode(edge.getTargetId());
            if (source == null || target == null) continue;

            // Použije metadáta z nodu s vyšším merge count (spoľahlivejší)
            RoadNode primary = source.getMergeCount() >= target.getMergeCount() ? source : target;
            if (primary.getRoadName() != null) {
                edge.setRoadName(primary.getRoadName());
                edge.setRoadClass(primary.getRoadClass());
                edge.setMaxSpeed(primary.getMaxSpeed());
            }
        }
    }

    /**
     * Zlúči nody, ktoré boli snapnuté na rovnaký GraphHopper edge,
     * ale len ak sú si blízke (v rámci mergeThreshold).
     * GH edge môže byť dlhý segment — bez distance check by sa zničila kontinuita.
     */
    private void mergeNodesOnSameEdge(RoadGraph graph, Map<Integer, List<RoadNode>> edgeIdToNodes,
                                       double mergeThreshold) {
        int mergeCount = 0;

        for (Map.Entry<Integer, List<RoadNode>> entry : edgeIdToNodes.entrySet()) {
            List<RoadNode> nodesOnSameEdge = new ArrayList<>(entry.getValue());
            if (nodesOnSameEdge.size() < 2) continue;

            // Zoraď podľa merge count (najspoľahlivejší prvý)
            nodesOnSameEdge.sort(Comparator.comparingInt(RoadNode::getMergeCount).reversed());

            // Greedy merge — zlúč len páry bližšie než mergeThreshold
            Set<RoadNode> removed = new HashSet<>();
            for (int i = 0; i < nodesOnSameEdge.size(); i++) {
                RoadNode primary = nodesOnSameEdge.get(i);
                if (removed.contains(primary)) continue;

                for (int j = i + 1; j < nodesOnSameEdge.size(); j++) {
                    RoadNode secondary = nodesOnSameEdge.get(j);
                    if (removed.contains(secondary)) continue;

                    double dist = GeoUtils.equirectangularDistance(
                            primary.getLat(), primary.getLon(),
                            secondary.getLat(), secondary.getLon()
                    );

                    if (dist > mergeThreshold) continue;

                    // Zlúč pozíciu + temporálne metadáta
                    primary.mergeWith(secondary.getLat(), secondary.getLon());
                    primary.updateTimestampRange(secondary.getFirstSeen());
                    primary.updateTimestampRange(secondary.getLastSeen());

                    // Presuň hrany zo secondary na primary
                    Set<RoadEdge> edges = graph.getEdgesOf(secondary);
                    for (RoadEdge edge : new ArrayList<>(edges)) {
                        RoadNode other = graph.getNode(
                                edge.getSourceId().equals(secondary.getId())
                                        ? edge.getTargetId()
                                        : edge.getSourceId()
                        );
                        if (other != null && !other.equals(primary)) {
                            double d = GeoUtils.haversineDistance(
                                    primary.getLat(), primary.getLon(),
                                    other.getLat(), other.getLon()
                            );
                            graph.addEdge(primary, other, d);
                        }
                    }

                    graph.removeNode(secondary);
                    removed.add(secondary);
                    mergeCount++;
                }
            }
        }

        if (mergeCount > 0) {
            log.debug("  Same-edge merge: {} nodes merged (threshold {}m)", mergeCount, mergeThreshold);
        }
    }

    /**
     * Prepočíta edge weights po snap korekcii súradníc.
     */
    private void recalculateEdgeWeights(RoadGraph graph) {
        for (RoadEdge edge : graph.getEdges()) {
            RoadNode source = graph.getNode(edge.getSourceId());
            RoadNode target = graph.getNode(edge.getTargetId());
            if (source == null || target == null) continue;

            double newDist = GeoUtils.haversineDistance(
                    source.getLat(), source.getLon(),
                    target.getLat(), target.getLon()
            );
            edge.setDistanceMeters(newDist);
            graph.getJGraphTGraph().setEdgeWeight(edge, newDist);
        }
    }

    /**
     * Odstráni nody označené ako off-road (voliteľné — závisí od use case).
     */
    private void removeOffRoadNodes(RoadGraph graph) {
        List<RoadNode> offRoadNodes = graph.getNodes().stream()
                .filter(RoadNode::isOffRoad)
                .toList();

        for (RoadNode node : offRoadNodes) {
            graph.removeNode(node);
        }

        if (!offRoadNodes.isEmpty()) {
            log.debug("  Removed {} off-road nodes", offRoadNodes.size());
        }
    }

    /**
     * Vytvorí kľúč pre lookup coordinate → node.
     * Zaokrúhlenie na 10 desatinných miest (~0.01mm) pre floating-point bezpečnosť.
     */
    private String coordKey(double lon, double lat) {
        return String.format("%.10f:%.10f", lon, lat);
    }

    @Override
    public GraphEntity saveGraphToDatabase(RoadGraph graph, String name) {
        GraphEntity graphEntity = new GraphEntity();
        graphEntity.setName(name);

        graphEntity.setNodes(graph.getNodes().stream()
                .map(node -> new GraphNodeEntity(node.getId(), node.getLat(), node.getLon()))
                .collect(Collectors.toList()));

        graphEntity.setEdges(graph.getEdges().stream()
                .map(edge -> new GraphEdgeEntity(edge.getSourceId(), edge.getTargetId(), edge.getDistanceMeters()))
                .collect(Collectors.toList()));

        return graphRepository.save(graphEntity);
    }

    @Override
    public RoadGraph importGraphFromDatabase(Long graphId) {
        return null;
    }

}
