package sk.master.backend.service;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.springframework.stereotype.Service;

import sk.master.backend.persistence.entity.GraphEdgeEntity;
import sk.master.backend.persistence.entity.GraphNodeEntity;
import sk.master.backend.persistence.entity.SavedGraph;
import sk.master.backend.persistence.model.MyGraph;
import sk.master.backend.persistence.repository.GraphRepository;

import static sk.master.backend.persistence.dto.UpdatePointsRequest.MyPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class GraphServiceImpl implements GraphService {

    private final GraphRepository graphRepository;
    private static final double SNAP_THRESHOLD_METERS = 15.0;

    public GraphServiceImpl(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    private record GraphNode(long id, double lat, double lon) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GraphNode graphNode = (GraphNode) o;
            return id == graphNode.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private record LatLon(double lat, double lon) {
    }

    @Override
    public MyGraph generateGraphFromGpx(GPX gpx) {
        Graph<GraphNode, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        List<GraphNode> spatialIndex = new ArrayList<>();
        AtomicLong idCounter = new AtomicLong(1);

        if (gpx.tracks() != null) {
            gpx.tracks()
                    .flatMap(Track::segments)
                    .forEach(segment -> {
                        List<LatLon> points = segment.getPoints().stream()
                                .map(wp -> new LatLon(wp.getLatitude().doubleValue(), wp.getLongitude().doubleValue()))
                                .toList();
                        processPath(points, graph, spatialIndex, idCounter);
                    });
        }

        return convertToMyGraph(graph);
    }

    @Override
    public MyGraph processPoints(MyGraph existingGraph, List<MyPoint> points) {
        if (existingGraph == null) {
            existingGraph = new MyGraph(new ArrayList<>(), new ArrayList<>());
        }

        // 1. Rehydrate graph
        Graph<GraphNode, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        List<GraphNode> spatialIndex = new ArrayList<>();
        long maxId = 0;

        for (MyGraph.Node node : existingGraph.getNodes()) {
            GraphNode gn = new GraphNode(node.getId(), node.getLat(), node.getLon());
            graph.addVertex(gn);
            spatialIndex.add(gn);
            if (node.getId() > maxId) maxId = node.getId();
        }

        Map<Long, GraphNode> nodeMap = spatialIndex.stream()
                .collect(Collectors.toMap(GraphNode::id, n -> n));

        for (MyGraph.Edge edge : existingGraph.getEdges()) {
            GraphNode source = nodeMap.get(edge.getSourceId());
            GraphNode target = nodeMap.get(edge.getTargetId());
            if (source != null && target != null) {
                DefaultWeightedEdge graphEdge = graph.addEdge(source, target);
                if (graphEdge != null) {
                    graph.setEdgeWeight(graphEdge, edge.getWeight());
                }
            }
        }

        // 2. Prepare execution
        AtomicLong idCounter = new AtomicLong(maxId + 1);

        List<LatLon> inputPoints = points.stream()
                .map(p -> new LatLon(p.getLat(), p.getLon()))
                .toList();

        // 3. Process
        processPath(inputPoints, graph, spatialIndex, idCounter);

        // 4. Return result
        return convertToMyGraph(graph);
    }

    private void processPath(List<LatLon> points,
                             Graph<GraphNode, DefaultWeightedEdge> graph,
                             List<GraphNode> spatialIndex,
                             AtomicLong idCounter) {

        GraphNode previousNode = null;

        for (LatLon point : points) {
            GraphNode currentNode = resolveNode(point.lat, point.lon, spatialIndex, graph, idCounter);

            if (previousNode != null && !previousNode.equals(currentNode)) {
                addEdgeIfNotExist(previousNode, currentNode, graph);
            }

            previousNode = currentNode;
        }
    }

    private GraphNode resolveNode(double lat, double lon,
                                  List<GraphNode> spatialIndex,
                                  Graph<GraphNode, DefaultWeightedEdge> graph,
                                  AtomicLong idCounter) {
        GraphNode nearestNode = null;
        double minDist = Double.MAX_VALUE;

        // Consider improving this with a spatial index for large datasets
        for (GraphNode candidate : spatialIndex) {
            double dist = haversineDistance(lat, lon, candidate.lat(), candidate.lon());
            if (dist < minDist) {
                minDist = dist;
                nearestNode = candidate;
            }
        }

        if (nearestNode != null && minDist <= SNAP_THRESHOLD_METERS) {
            return nearestNode;
        } else {
            return createNewNode(lat, lon, graph, spatialIndex, idCounter);
        }
    }

    private GraphNode createNewNode(double lat, double lon,
                                    Graph<GraphNode, DefaultWeightedEdge> graph,
                                    List<GraphNode> spatialIndex,
                                    AtomicLong idCounter) {
        GraphNode node = new GraphNode(idCounter.getAndIncrement(), lat, lon);
        graph.addVertex(node);
        spatialIndex.add(node);
        return node;
    }

    private void addEdgeIfNotExist(GraphNode source, GraphNode target, Graph<GraphNode, DefaultWeightedEdge> graph) {
        if (!graph.containsEdge(source, target)) {
            DefaultWeightedEdge edge = graph.addEdge(source, target);
            if (edge != null) {
                double dist = haversineDistance(source.lat(), source.lon(), target.lat(), target.lon());
                graph.setEdgeWeight(edge, dist);
            }
        }
        // Logic to increase edge weight/frequency could be added here
    }

    private MyGraph convertToMyGraph(Graph<GraphNode, DefaultWeightedEdge> graph) {
        List<MyGraph.Node> nodes = new ArrayList<>();
        List<MyGraph.Edge> edges = new ArrayList<>();

        for (GraphNode node : graph.vertexSet()) {
            nodes.add(new MyGraph.Node(node.id(), node.lat(), node.lon()));
        }

        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            GraphNode source = graph.getEdgeSource(edge);
            GraphNode target = graph.getEdgeTarget(edge);
            double weight = graph.getEdgeWeight(edge);
            edges.add(new MyGraph.Edge(source.id(), target.id(), weight));
        }

        return new MyGraph(nodes, edges);
    }

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

        savedGraph.setNodes(graph.getNodes().stream()
                .map(node -> new GraphNodeEntity(node.getId(), node.getLat(), node.getLon()))
                .collect(Collectors.toList()));

        savedGraph.setEdges(graph.getEdges().stream()
                .map(edge -> new GraphEdgeEntity(edge.getSourceId(), edge.getTargetId(), edge.getWeight()))
                .collect(Collectors.toList()));

        return graphRepository.save(savedGraph);
    }

    @Override
    public MyGraph importGraphFromDatabase(Long graphId) {
        return null;
    }

}