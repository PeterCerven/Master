package sk.master.backend.persistence.model;

import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;
import sk.master.backend.util.GeoUtils;

import java.util.*;
import java.util.stream.Collectors;

public class RoadGraph {

    private final Graph<RoadNode, RoadEdge> graph;
    private final Quadtree spatialIndex;
    private final Map<String, RoadNode> nodeMap;

    public RoadGraph() {
        this.graph = new SimpleWeightedGraph<>(RoadEdge.class);
        this.spatialIndex = new Quadtree();
        this.nodeMap = new HashMap<>();
    }

    // =====================================================================
    // Node operácie
    // =====================================================================

    public void addNode(RoadNode node) {
        if (nodeMap.containsKey(node.getId())) {
            return; // už existuje
        }
        graph.addVertex(node);
        nodeMap.put(node.getId(), node);
        spatialIndex.insert(envelopeOf(node), node);
    }

    public RoadNode getNode(String id) {
        return nodeMap.get(id);
    }

    public Collection<RoadNode> getNodes() {
        return Collections.unmodifiableCollection(nodeMap.values());
    }

    public int getNodeCount() {
        return nodeMap.size();
    }

    public void removeNode(RoadNode node) {
        graph.removeVertex(node); // automaticky zmaže aj pridružené hrany
        nodeMap.remove(node.getId());
        spatialIndex.remove(envelopeOf(node), node);
    }

    // =====================================================================
    // Edge operácie
    // =====================================================================

    /**
     * Pridá hranu medzi dva nody. Ak hrana už existuje, nič sa nestane.
     * Weight sa nastaví na vzdialenosť v metroch.
     */
    public void addEdge(RoadNode source, RoadNode target, double distanceMeters) {
        if (graph.containsEdge(source, target)) {
            graph.getEdge(source, target);
            return;
        }
        RoadEdge edge = new RoadEdge(source.getId(), target.getId(), distanceMeters);
        graph.addEdge(source, target, edge);
        graph.setEdgeWeight(edge, distanceMeters);
    }

    public Set<RoadEdge> getEdges() {
        return Collections.unmodifiableSet(graph.edgeSet());
    }

    public Set<RoadEdge> getEdgesOf(RoadNode node) {
        if (!graph.containsVertex(node)) {
            return Collections.emptySet();
        }
        return graph.edgesOf(node);
    }

    public int getEdgeCount() {
        return graph.edgeSet().size();
    }

    // =====================================================================
    // Priestorové vyhľadávanie (cez JTS Quadtree)
    // =====================================================================

    /**
     * Nájde najbližší node k daným súradniciam v rámci maxDistance (v metroch).
     * Používa equirectangular approximáciu pre rýchlosť.
     */
    public RoadNode findNearest(double lat, double lon, double maxDistanceMeters) {
        // Preveď maxDistance na približné stupne pre envelope query
        double degreeApprox = maxDistanceMeters / 111_320.0; // ~1° lat = 111.32 km
        Envelope searchEnv = new Envelope(
                lon - degreeApprox, lon + degreeApprox,
                lat - degreeApprox, lat + degreeApprox
        );

        @SuppressWarnings("unchecked")
        List<RoadNode> candidates = spatialIndex.query(searchEnv);

        RoadNode nearest = null;
        double minDist = Double.MAX_VALUE;

        for (RoadNode candidate : candidates) {
            double dist = GeoUtils.equirectangularDistance(lat, lon, candidate.getLat(), candidate.getLon());
            if (dist < minDist && dist <= maxDistanceMeters) {
                minDist = dist;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * Nájde K najbližších nodov k daným súradniciam.
     */
    public List<RoadNode> findKNearest(double lat, double lon, int k, double maxDistanceMeters) {
        double degreeApprox = maxDistanceMeters / 111_320.0;
        Envelope searchEnv = new Envelope(
                lon - degreeApprox, lon + degreeApprox,
                lat - degreeApprox, lat + degreeApprox
        );

        @SuppressWarnings("unchecked")
        List<RoadNode> candidates = spatialIndex.query(searchEnv);

        return candidates.stream()
                .map(node -> new AbstractMap.SimpleEntry<>(
                        node,
                        GeoUtils.equirectangularDistance(lat, lon, node.getLat(), node.getLon())
                ))
                .filter(e -> e.getValue() <= maxDistanceMeters)
                .sorted(Comparator.comparingDouble(AbstractMap.SimpleEntry::getValue))
                .limit(k)
                .map(AbstractMap.SimpleEntry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Nájde všetky nody v danom okruhu (v metroch).
     *
     * <p>Používa Quadtree envelope query (obdĺžnik) ako rýchly filter,
     * potom equirectangular refinement pre presný kruhový výber.
     * Ideálne pre Gabriel pruning, density queries a collision detection.
     *
     * @param lat           stred kruhu — zemepisná šírka
     * @param lon           stred kruhu — zemepisná dĺžka
     * @param radiusMeters  polomer v metroch
     * @param exclude        nody, ktoré sa majú vynechať z výsledku (napr. source/target hrany)
     * @return všetky nody v kruhu okrem vylúčených
     */
    public List<RoadNode> findInRadius(double lat, double lon, double radiusMeters,
                                       Set<RoadNode> exclude) {
        double degreeApprox = radiusMeters / 111_320.0;
        Envelope searchEnv = new Envelope(
                lon - degreeApprox, lon + degreeApprox,
                lat - degreeApprox, lat + degreeApprox
        );

        @SuppressWarnings("unchecked")
        List<RoadNode> candidates = spatialIndex.query(searchEnv);

        List<RoadNode> result = new ArrayList<>();
        for (RoadNode candidate : candidates) {
            if (exclude != null && exclude.contains(candidate)) continue;
            double dist = GeoUtils.equirectangularDistance(lat, lon, candidate.getLat(), candidate.getLon());
            if (dist <= radiusMeters) {
                result.add(candidate);
            }
        }
        return result;
    }

    // =====================================================================
    // Prístup k JGraphT grafu pre pokročilé algoritmy
    // =====================================================================

    /**
     * Vráti underlying JGraphT graf — pre Dijkstra, A*, atď.
     */
    public Graph<RoadNode, RoadEdge> getJGraphTGraph() {
        return graph;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Envelope envelopeOf(RoadNode node) {
        return new Envelope(new Coordinate(node.getLon(), node.getLat()));
    }
}

