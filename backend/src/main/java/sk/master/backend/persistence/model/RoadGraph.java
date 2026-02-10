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
    // Node operations
    // =====================================================================

    public void addNode(RoadNode node) {
        if (nodeMap.containsKey(node.getId())) {
            return; // already exists
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
        graph.removeVertex(node); // automatically removes associated edges
        nodeMap.remove(node.getId());
        spatialIndex.remove(envelopeOf(node), node);
    }

    // =====================================================================
    // Edge operations
    // =====================================================================

    /**
     * Adds an edge between two nodes. If the edge already exists, nothing happens.
     * Weight is set to distance in meters.
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
    // Spatial search (via JTS Quadtree)
    // =====================================================================

    /**
     * Finds the nearest node to given coordinates within maxDistance (in meters).
     * Uses equirectangular approximation for speed.
     */
    public RoadNode findNearest(double lat, double lon, double maxDistanceMeters) {
        // Convert maxDistance to approximate degrees for envelope query
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
     * Finds the K nearest nodes to given coordinates.
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
     * Finds all nodes within the given radius (in meters).
     *
     * <p>Uses Quadtree envelope query (rectangle) as a fast filter,
     * then equirectangular refinement for precise circular selection.
     * Ideal for Gabriel pruning, density queries, and collision detection.
     *
     * @param lat           circle center — latitude
     * @param lon           circle center — longitude
     * @param radiusMeters  radius in meters
     * @param exclude       nodes to exclude from the result (e.g. source/target of an edge)
     * @return all nodes within the circle excluding excluded ones
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
    // Access to JGraphT graph for advanced algorithms
    // =====================================================================

    /**
     * Returns the underlying JGraphT graph — for Dijkstra, A*, etc.
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

