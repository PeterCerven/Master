package sk.master.backend.service;

import org.springframework.stereotype.Service;
import sk.master.backend.persistence.entity.GraphEdgeEntity;
import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.entity.GraphNodeEntity;
import sk.master.backend.persistence.model.Position;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.repository.GraphRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GraphServiceImpl implements GraphService {

    private final GraphRepository graphRepository;
    private static final double SNAP_THRESHOLD_METERS = 15.0;

    public GraphServiceImpl(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public RoadGraph processPoints(RoadGraph existingGraph, List<Position> positions) {
        return null;
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
    public GraphEntity saveGraph(RoadGraph graph, String name) {
        GraphEntity graphEntity = new GraphEntity();
        graphEntity.setName(name);

        graphEntity.setNodes(graph.getNodes().stream()
                .map(node -> new GraphNodeEntity(node.getId(), node.getLat(), node.getLon()))
                .collect(Collectors.toList()));

        graphEntity.setEdges(graph.getEdges().stream()
                .map(edge -> new GraphEdgeEntity(edge.getSourceId(), edge.getTargetId(), edge.getWeight()))
                .collect(Collectors.toList()));

        return graphRepository.save(graphEntity);
    }

    @Override
    public RoadGraph importGraphFromDatabase(Long graphId) {
        return null;
    }

    // step 1.
    private void convertData() {

    }

    // step 2.
    private void preProcessData() {
    }

    // step 3.
    private void spatialIndexing() {

    }

    // step 4.
    private void incrementalProcessing() {

    }

    // step 5.
    private void mapMatching() {

    }

}