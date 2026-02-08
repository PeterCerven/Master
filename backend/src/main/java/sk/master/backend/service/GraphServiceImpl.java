package sk.master.backend.service;

import com.uber.h3core.H3Core;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
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

    private final PipelineConfig config;
    private final MapMatchingService mapMatchingService;
    private final H3Core h3;
    private final GraphRepository graphRepository;

    public GraphServiceImpl(GraphRepository graphRepository, PipelineConfig config, MapMatchingServiceGraphHopper mapMatchingService) {
        this.graphRepository = graphRepository;
        this.config = config;
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
            log.warn("Prázdny list pozícií — vraciam existujúci graf alebo prázdny nový.");
            return existingGraph != null ? existingGraph : new RoadGraph();
        }

        log.info("=== Štart pipeline: {} vstupných bodov ===", positionalData.size());

        // Krok 1: Predspracovanie
        List<PositionalData> filtered = preprocess(positionalData);
        log.info("Krok 1 (predspracovanie): {} → {} bodov", positionalData.size(), filtered.size());

        if (filtered.isEmpty()) {
            log.warn("Všetky body boli odfiltrované — vraciam existujúci graf alebo prázdny.");
            return existingGraph != null ? existingGraph : new RoadGraph();
        }

        // Krok 2: H3 Spatial Index + deduplikácia
        List<PositionalData> indexed = spatialIndexAndDedup(filtered);
        log.info("Krok 2 (H3 dedup): {} → {} bodov", filtered.size(), indexed.size());

        // Krok 3: Inkrementálne vkladanie do grafu
        RoadGraph graph = existingGraph != null ? existingGraph : new RoadGraph();
        buildGraph(graph, indexed);
        log.info("Krok 3 (graf): {} nodov, {} hrán", graph.getNodeCount(), graph.getEdgeCount());

        // Krok 4: Map Matching (GraphHopper)
        applyMapMatching(graph);
        log.info("Krok 4 (map matching): {} nodov, {} hrán (po korekcii)",
                graph.getNodeCount(), graph.getEdgeCount());

        log.info("=== Pipeline dokončený ===");
        return graph;
    }

    private List<PositionalData> preprocess(List<PositionalData> positionalData) {
        List<PositionalData> result = new ArrayList<>();

        // 1a) Validácia súradníc + bounding box
        for (PositionalData p : positionalData) {
            if (isValidCoordinate(p) && isWithinBoundingBox(p)) {
                result.add(p);
            }
        }
        log.debug("  Po validácii + bounding box: {}", result.size());

        // 1b) Zoradenie podľa timestamp (body bez timestamp idú na koniec)
        result.sort(Comparator.comparing(
                PositionalData::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        log.debug("  Po zoradení podľa timestamp");

        // 1c) Speed-based outlier removal — body s nerealistickou rýchlosťou voči predchádzajúcemu
        result = removeSpeedOutliers(result);
        log.debug("  Po speed filter: {}", result.size());

        // 1d) Exact duplicate removal (cez HashSet — využíva PositionalData.equals/hashCode)
        result = new ArrayList<>(new LinkedHashSet<>(result));
        log.debug("  Po exact dedup: {}", result.size());

        // 1e) Near-duplicate removal
        result = removeNearDuplicates(result);
        log.debug("  Po near-dedup: {}", result.size());

        // 1f) Density-based outlier removal
        result = removeOutliers(result);
        log.debug("  Po outlier removal: {}", result.size());

        return result;
    }

    /**
     * Overí, či sú súradnice platné a nie sú null-island (0,0).
     */
    private boolean isValidCoordinate(PositionalData p) {
        if (p.getLat() < -90 || p.getLat() > 90 || p.getLon() < -180 || p.getLon() > 180) {
            return false;
        }
        // Null island check — (0,0) je v Guinejskom zálive, určite nie na Slovensku
        return !(Math.abs(p.getLat()) < 0.001 && Math.abs(p.getLon()) < 0.001);
    }

    /**
     * Overí, či bod leží v rámci konfigurovaného bounding boxu (default: Slovensko).
     */
    private boolean isWithinBoundingBox(PositionalData p) {
        return p.getLat() >= config.getMinLat() && p.getLat() <= config.getMaxLat()
                && p.getLon() >= config.getMinLon() && p.getLon() <= config.getMaxLon();
    }

    /**
     * Odstráni body, ktoré implikujú nerealistickú rýchlosť voči predchádzajúcemu bodu.
     * Predpokladá, že vstupný zoznam je zoradený podľa timestamp.
     * Body bez timestamp sú ponechané (nie je možné vypočítať rýchlosť).
     * Rýchlosť sa počíta len medzi po sebe idúcimi bodmi v rámci rovnakej jazdy
     * (medzera > tripGapMinutes znamená novú jazdu).
     */
    private List<PositionalData> removeSpeedOutliers(List<PositionalData> positionalData) {
        double maxSpeedKmh = config.getMaxSpeedKmh();
        long tripGapMinutes = config.getTripGapMinutes();
        double maxSpeedMs = maxSpeedKmh / 3.6; // km/h → m/s

        List<PositionalData> result = new ArrayList<>();
        PositionalData prev = null;
        int removed = 0;

        for (PositionalData p : positionalData) {
            if (prev == null || p.getTimestamp() == null || prev.getTimestamp() == null) {
                // Prvý bod alebo body bez timestamp — vždy ponechaj
                result.add(p);
                if (p.getTimestamp() != null) {
                    prev = p;
                }
                continue;
            }

            Duration timeDiff = Duration.between(prev.getTimestamp(), p.getTimestamp());
            long seconds = timeDiff.getSeconds();

            // Ak je medzera väčšia než trip gap, je to nová jazda — resetuj
            if (seconds > tripGapMinutes * 60) {
                result.add(p);
                prev = p;
                continue;
            }

            // Ak je časový rozdiel nulový alebo záporný, ponechaj bod (rovnaký čas)
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
                // Nemeníme prev — preskočený bod neovplyvní ďalšie porovnania
            }
        }

        if (removed > 0) {
            log.debug("  Speed filter: odstránených {} bodov (>{} km/h)", removed, maxSpeedKmh);
        }
        return result;
    }

    /**
     * Odstráni near-duplicate body — body bližšie než threshold sa zlúčia.
     * Používa grid-based spatial hashing pre O(n) priemernú komplexnosť.
     */
    private List<PositionalData>
    removeNearDuplicates(List<PositionalData> positionalData) {
        double threshold = config.getNearDuplicateThresholdM();
        // Veľkosť grid celly ~ threshold v stupňoch
        double cellSize = threshold / 111_320.0;

        Map<String, PositionalData> gridMap = new LinkedHashMap<>();

        for (PositionalData p : positionalData) {
            String cellKey = (int) (p.getLat() / cellSize) + ":" + (int) (p.getLon() / cellSize);
            if (!gridMap.containsKey(cellKey)) {
                gridMap.put(cellKey, p);
            }
            // Ak cella už obsadená, kontroluj vzdialenosť
            // Ak sú blízko, skip; inak použi unikátny kľúč
            else {
                PositionalData existing = gridMap.get(cellKey);
                double dist = GeoUtils.equirectangularDistance(
                        p.getLat(), p.getLon(), existing.getLat(), existing.getLon()
                );
                if (dist >= threshold) {
                    // Sú v rovnakej celle ale dostatočne ďaleko — použi composite key
                    String uniqueKey = cellKey + ":" + gridMap.size();
                    gridMap.put(uniqueKey, p);
                }
                // else: near-duplicate, skip
            }
        }
        return new ArrayList<>(gridMap.values());
    }

    /**
     * Odstráni izolované body (outliers) — body s menej než minNeighbors
     * v okruhu outlierRadius sú považované za šum.
     *
     * <p>Používa dočasný {@link Quadtree} pre O(n log n) range queries
     * namiesto O(n²) brute force. Quadtree podporuje dynamický insert,
     * takže rovnaký pattern funguje aj pre inkrementálne scenáre.
     */
    private List<PositionalData> removeOutliers(List<PositionalData> positionalData) {
        int minNeighbors = config.getOutlierMinNeighbors();
        double radius = config.getOutlierRadiusM();

        // 1) Vlož všetky body do dočasného Quadtree
        Quadtree tree = new Quadtree();
        for (PositionalData p : positionalData) {
            Envelope env = new Envelope(
                    new Coordinate(p.getLon(), p.getLat())
            );
            tree.insert(env, p);
        }

        // 2) Pre každý bod sprav range query namiesto iterácie cez všetky body
        double degreeApprox = radius / 111_320.0;
        List<PositionalData> result = new ArrayList<>();

        for (PositionalData p : positionalData) {
            Envelope searchEnv = new Envelope(
                    p.getLon() - degreeApprox, p.getLon() + degreeApprox,
                    p.getLat() - degreeApprox, p.getLat() + degreeApprox
            );

            @SuppressWarnings("unchecked")
            List<PositionalData> candidates = tree.query(searchEnv);

            // Refinement: obdĺžnik → kruh (skontroluj reálnu vzdialenosť)
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
    // KROK 2: H3 SPATIAL INDEX + DEDUPLIKÁCIA
    // =========================================================================

    /**
     * Indexuje body do H3 hexagonálnych buniek a deduplikuje —
     * body v rovnakej resolution-11 bunke (~29m) sa zlúčia na centroid.
     * Zachováva najskorší a najnovší timestamp z bodov v bunke.
     */
    private List<PositionalData> spatialIndexAndDedup(List<PositionalData> positionalData) {
        int resolution = config.getH3DedupResolution();

        // Zoskupi body podľa H3 cell ID
        Map<Long, List<PositionalData>> cellMap = new HashMap<>();
        for (PositionalData p : positionalData) {
            long cellId = h3.latLngToCell(p.getLat(), p.getLon(), resolution);
            cellMap.computeIfAbsent(cellId, _ -> new ArrayList<>()).add(p);
        }

        // Pre každú bunku vráti centroid všetkých bodov v bunke s najskorším timestamp
        List<PositionalData> deduplicated = new ArrayList<>();
        for (Map.Entry<Long, List<PositionalData>> entry : cellMap.entrySet()) {
            List<PositionalData> cellPoints = entry.getValue();
            double avgLat = cellPoints.stream().mapToDouble(PositionalData::getLat).average().orElse(0);
            double avgLon = cellPoints.stream().mapToDouble(PositionalData::getLon).average().orElse(0);

            // Zachovaj najskorší timestamp z bodov v bunke (reprezentatívny čas)
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
    // KROK 3: INKREMENTÁLNE VKLADANIE DO GRAFU
    // =========================================================================

    /**
     * Vloží body do grafu — buď merge do existujúcich nodov, alebo vytvor nové.
     * Pre nový graf najprv spustí DBSCAN clustering a Delaunay triangulation.
     * Pre existujúci graf použije inkrementálny merge-or-create pattern.
     */
    private void buildGraph(RoadGraph graph, List<PositionalData> positionalData) {
        if (graph.getNodeCount() == 0) {
            // Nový graf — spusti plný pipeline: DBSCAN → Delaunay → Gabriel pruning
            buildNewGraph(graph, positionalData);
        } else {
            // Existujúci graf — inkrementálne vloženie
            incrementalInsert(graph, positionalData);
        }
    }

    /**
     * Buduje nový graf od nuly pomocou DBSCAN + Delaunay + Gabriel pruning.
     */
    private void buildNewGraph(RoadGraph graph, List<PositionalData> positionalData) {
        // 3a) DBSCAN clustering — nájde cestné koridory
        List<PositionalData> clusterCentroids = clusterWithDbscan(positionalData);
        log.debug("  DBSCAN: {} klastrov (z {} bodov)", clusterCentroids.size(), positionalData.size());

        if (clusterCentroids.isEmpty()) {
            // Fallback: ak DBSCAN nič nenájde, použi všetky body priamo
            log.warn("  DBSCAN nenašiel žiadne klastre — použijem všetky body priamo");
            clusterCentroids = positionalData;
        }

        // 3b) Vytvor nody z centroidov (vrátane temporálnych metadát)
        List<RoadNode> nodes = new ArrayList<>();
        for (PositionalData p : clusterCentroids) {
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
        pruneByMaxLength(graph);
    }

    /**
     * Spustí DBSCAN clustering a vráti centroidy klastrov.
     * Body označené ako šum sú odfiltrované.
     */
    private List<PositionalData> clusterWithDbscan(List<PositionalData> positionalData) {
        // Apache Commons Math DBSCAN vyžaduje Clusterable wrapper
        List<ClusterablePosition> clusterablePoints = positionalData.stream()
                .map(ClusterablePosition::new)
                .collect(Collectors.toList());

        // eps musí byť v rovnakých jednotkách ako DistanceMeasure
        // Používame vlastnú Haversine DistanceMeasure, takže eps je v metroch
        DBSCANClusterer<ClusterablePosition> clusterer = new DBSCANClusterer<>(
                config.getDbscanEpsMeters(),
                config.getDbscanMinPts(),
                new HaversineDistanceMeasure()
        );

        List<Cluster<ClusterablePosition>> clusters = clusterer.cluster(clusterablePoints);

        // Pre každý klaster vráti centroid s najskorším timestamp
        List<PositionalData> centroids = new ArrayList<>();
        for (Cluster<ClusterablePosition> cluster : clusters) {
            List<ClusterablePosition> points = cluster.getPoints();
            double avgLat = points.stream().mapToDouble(p -> p.positionalData().getLat()).average().orElse(0);
            double avgLon = points.stream().mapToDouble(p -> p.positionalData().getLon()).average().orElse(0);

            Instant earliest = points.stream()
                    .map(p -> p.positionalData().getTimestamp())
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);

            centroids.add(new PositionalData(avgLat, avgLon, earliest));
        }
        return centroids;
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
        log.debug("  Gabriel pruning: odstránených {} hrán (z {} testovaných)",
                toRemove.size(), graph.getEdgeCount() + toRemove.size());
    }

    /**
     * Odstráni hrany dlhšie než maxEdgeLength.
     */
    private void pruneByMaxLength(RoadGraph graph) {
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
        log.debug("  Max-length pruning: odstránených {} hrán (>{} m)", toRemove.size(), maxLen);
    }

    /**
     * Inkrementálne vloží nové body do existujúceho grafu.
     * Pattern: merge-or-create + KNN spojenie.
     */
    private void incrementalInsert(RoadGraph graph, List<PositionalData> positionalData) {
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

                for (RoadNode neighbor : neighbors) {
                    if (neighbor.equals(newNode)) continue;
                    double dist = GeoUtils.haversineDistance(
                            newNode.getLat(), newNode.getLon(),
                            neighbor.getLat(), neighbor.getLon()
                    );
                    if (dist <= maxEdgeLen) {
                        graph.addEdge(newNode, neighbor, dist);
                    }
                }
                created++;
            }
        }
        log.debug("  Inkrementálne: {} merged, {} created", merged, created);
    }

    // =========================================================================
    // KROK 4: MAP MATCHING (GRAPHHOPPER)
    // =========================================================================

    /**
     * Koriguje pozície nodov pomocou GraphHopper snap-to-road
     * a obohacuje nody/hrany o cestné metadáta.
     */
    private void applyMapMatching(RoadGraph graph) {
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

        // Zlúč nody, ktoré boli snapnuté na rovnaký GraphHopper edge
        mergeNodesOnSameEdge(graph, edgeIdToNodes);

        // Prepočítaj edge weights na reálne vzdialenosti (po snap korekcii)
        recalculateEdgeWeights(graph);

        // Voliteľne: odstráň off-road nody
        removeOffRoadNodes(graph);
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
     * Zlúči nody, ktoré boli snapnuté na rovnaký GraphHopper edge.
     * To znamená, že ležia na rovnakom cestnom segmente.
     */
    private void mergeNodesOnSameEdge(RoadGraph graph, Map<Integer, List<RoadNode>> edgeIdToNodes) {
        int mergeCount = 0;

        for (Map.Entry<Integer, List<RoadNode>> entry : edgeIdToNodes.entrySet()) {
            List<RoadNode> nodesOnSameEdge = entry.getValue();
            if (nodesOnSameEdge.size() < 2) continue;

            // Ponechaj node s najvyšším merge count ako "primary"
            nodesOnSameEdge.sort(Comparator.comparingInt(RoadNode::getMergeCount).reversed());
            RoadNode primary = nodesOnSameEdge.getFirst();

            for (int i = 1; i < nodesOnSameEdge.size(); i++) {
                RoadNode secondary = nodesOnSameEdge.get(i);

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
                        double dist = GeoUtils.haversineDistance(
                                primary.getLat(), primary.getLon(),
                                other.getLat(), other.getLon()
                        );
                        graph.addEdge(primary, other, dist);
                    }
                }

                // Odstráň secondary node
                graph.removeNode(secondary);
                mergeCount++;
            }
        }

        if (mergeCount > 0) {
            log.debug("  Same-edge merge: {} nodov zlúčených", mergeCount);
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
            log.debug("  Odstránených {} off-road nodov", offRoadNodes.size());
        }
    }

    // =========================================================================
    // HELPER TRIEDY
    // =========================================================================

    /**
         * Wrapper pre Apache Commons Math DBSCAN — obaľuje Position do Clusterable.
         */
        private record ClusterablePosition(PositionalData positionalData) implements Clusterable {

        @Override
            public double[] getPoint() {
                // DBSCAN bude volať DistanceMeasure.compute() na tieto body
                return new double[]{positionalData.getLat(), positionalData.getLon()};
            }
        }

    /**
     * Haversine DistanceMeasure pre DBSCAN — eps je potom priamo v metroch.
     */
    private static class HaversineDistanceMeasure
            implements org.apache.commons.math3.ml.distance.DistanceMeasure {

        @Override
        public double compute(double[] a, double[] b) {
            return GeoUtils.equirectangularDistance(a[0], a[1], b[0], b[1]);
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