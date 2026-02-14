package sk.master.backend.persistence.model;

import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.*;

public class RoadGraph {

    private final Graph<RoadNode, RoadEdge> graph;
    private final Quadtree spatialIndex;
    private final Map<String, RoadNode> nodeMap;

    public RoadGraph() {
        this.graph = new SimpleWeightedGraph<>(RoadEdge.class);
        this.spatialIndex = new Quadtree();
        this.nodeMap = new HashMap<>();
    }


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

    private Envelope envelopeOf(RoadNode node) {
        return new Envelope(new Coordinate(node.getLon(), node.getLat()));
    }
}

