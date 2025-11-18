package sk.master.backend.service;


import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sk.master.backend.persistence.model.MyGraph;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class GraphServiceImpl implements GraphService {

    private static final Logger logger = LoggerFactory.getLogger(GraphServiceImpl.class);

    private Graph<GraphNode, DefaultWeightedEdge> graph;
    private List<GraphNode> allNodes;

    private long nodeIdCounter = 1;
    private static final double SNAP_THRESHOLD_METERS = 15.0;
    private static final String GPX_FILE_PATH = "onthegomap-0.7-km-route.gpx";

    @PostConstruct
    public void init() {
        this.graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        this.allNodes = new ArrayList<>();

        Path tempFile = null;
        try {
            logger.info("Starting to load GPX file from resources: {}", GPX_FILE_PATH);
            ClassPathResource resource = new ClassPathResource(GPX_FILE_PATH);
            logger.info("Resource exists: {}, isReadable: {}", resource.exists(), resource.isReadable());

            tempFile = Files.createTempFile("gpx-temp-", ".gpx");
            logger.info("Created temporary file: {}", tempFile);

            try (InputStream inputStream = resource.getInputStream()) {
                long bytescopied = Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied {} bytes to temporary file", bytescopied);
            }

            logger.info("Reading GPX file from temporary path...");
            GPX gpx = GPX.read(tempFile);
            logger.info("GPX file loaded successfully. Processing tracks...");

            gpx.tracks()
                    .flatMap(Track::segments)
                    .forEach(segment -> {
                        try {
                            logger.info("Processing segment with {} points", segment.getPoints().size());
                            processSegment(segment);
                        } catch (Exception e) {
                            logger.error("Error processing segment", e);
                            throw new RuntimeException("Error processing segment", e);
                        }
                    });

            logger.info("Graph created with {} nodes and {} edges",
                       graph.vertexSet().size(), graph.edgeSet().size());

        } catch (IOException e) {
            logger.error("Failed to load GPX file from resources: {}", GPX_FILE_PATH, e);
            throw new RuntimeException("Failed to load GPX file from resources: " + GPX_FILE_PATH, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    logger.debug("Deleted temporary file: {}", tempFile);
                } catch (IOException e) {
                    logger.warn("Could not delete temp GPX file: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public MyGraph generateGraph() {
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
}
