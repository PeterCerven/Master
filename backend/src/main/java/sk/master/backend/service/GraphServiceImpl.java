package sk.master.backend.service;


import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.springframework.stereotype.Service;
import sk.master.backend.persistence.entity.GraphEdgeEntity;
import sk.master.backend.persistence.entity.GraphNodeEntity;
import sk.master.backend.persistence.entity.SavedGraph;
import sk.master.backend.persistence.model.MyGraph;
import sk.master.backend.persistence.repository.GraphRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GraphServiceImpl implements GraphService {
    private final Graph<GraphNode, DefaultWeightedEdge> graph;
    private final List<GraphNode> allNodes;
    private final GraphRepository graphRepository;

    public GraphServiceImpl(GraphRepository graphRepository) {
        this.graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        this.allNodes = new ArrayList<>();
        this.graphRepository = graphRepository;
    }

    private long nodeIdCounter = 1;
    private static final double SNAP_THRESHOLD_METERS = 15.0;

    @Override
    public MyGraph generateGraph(GPX gpx) {
        gpx.tracks()
                .flatMap(Track::segments)
                .forEach(this::processSegment);

        List<MyGraph.Node> nodes = new ArrayList<>();
        List<MyGraph.Edge> edges = new ArrayList<>();

        // Convert graph nodes to MyGraph.Node
        for (GraphNode node : graph.vertexSet()) {
            nodes.add(new MyGraph.Node(node.id(), node.lat(), node.lon()));
        }

        // Convert graph edges to MyGraph.Edge
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            GraphNode source = graph.getEdgeSource(edge);
            GraphNode target = graph.getEdgeTarget(edge);
            double weight = graph.getEdgeWeight(edge);
            edges.add(new MyGraph.Edge(source.id(), target.id(), weight));
        }

        return new MyGraph(nodes, edges);
    }

    // Record pre náš uzol
    public record GraphNode(long id, double lat, double lon) {
    }

    private void processSegment(TrackSegment segment) {
        GraphNode previousNode = null;

        for (WayPoint wp : segment.getPoints()) {
            // Konverzia jpx WayPoint na súradnice
            double lat = wp.getLatitude().doubleValue();
            double lon = wp.getLongitude().doubleValue();

            // A. Nájdi alebo vytvor uzol (Inkrementálna logika)
            GraphNode currentNode = resolveNode(lat, lon);

            // B. Vytvor hranu
            if (previousNode != null && !previousNode.equals(currentNode)) {
                addEdgeIfNotExist(previousNode, currentNode);
            }

            previousNode = currentNode;
        }
    }

    private GraphNode resolveNode(double lat, double lon) {
        GraphNode nearestNode = null;
        double minDist = Double.MAX_VALUE;

        for (GraphNode candidate : allNodes) {
            double dist = haversineDistance(lat, lon, candidate.lat(), candidate.lon());
            if (dist < minDist) {
                minDist = dist;
                nearestNode = candidate;
            }
        }

        if (nearestNode != null && minDist <= SNAP_THRESHOLD_METERS) {
            return nearestNode;
        } else {
            return createNewNode(lat, lon);
        }
    }

    private GraphNode createNewNode(double lat, double lon) {
        GraphNode node = new GraphNode(nodeIdCounter++, lat, lon);

        graph.addVertex(node);
        allNodes.add(node);

        return node;
    }

    private void addEdgeIfNotExist(GraphNode source, GraphNode target) {
        if (!graph.containsEdge(source, target)) {
            DefaultWeightedEdge edge = graph.addEdge(source, target);

            double dist = haversineDistance(source.lat(), source.lon(), target.lat(), target.lon());
            graph.setEdgeWeight(edge, dist);
        } else {
            // zvýšenie váhy/frekvencie existujúcej hrany
        }
    }

    // Haversine vzorec
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double latDist = Math.toRadians(lat2 - lat1);
        double lonDist = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDist / 2) * Math.sin(latDist / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDist / 2) * Math.sin(lonDist / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override
    public SavedGraph saveGraph(MyGraph graph, String name) {
        SavedGraph savedGraph = new SavedGraph();
        savedGraph.setName(name);

        // Convert MyGraph.Node to GraphNodeEntity
        List<GraphNodeEntity> nodeEntities = graph.getNodes().stream()
                .map(node -> new GraphNodeEntity(node.getId(), node.getLat(), node.getLon()))
                .collect(Collectors.toList());
        savedGraph.setNodes(nodeEntities);

        // Convert MyGraph.Edge to GraphEdgeEntity
        List<GraphEdgeEntity> edgeEntities = graph.getEdges().stream()
                .map(edge -> new GraphEdgeEntity(edge.getSourceId(), edge.getTargetId(), edge.getWeight()))
                .collect(Collectors.toList());
        savedGraph.setEdges(edgeEntities);

        return graphRepository.save(savedGraph);
    }
}
