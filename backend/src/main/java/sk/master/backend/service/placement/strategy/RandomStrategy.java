package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


@Component
public class RandomStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(RandomStrategy.class);

    @Override
    public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
        int k = params.getK();
        double maxRadiusMeters = params.getMaxRadiusMeters();
        int iterations = params.getIterations();
        Graph<RoadNode, RoadEdge> graph = roadGraph.getGraph();
        Set<RoadNode> allNodes = graph.vertexSet();

        if (allNodes.isEmpty()) {
            return new PlacementResult(List.of(), 0, Map.of());
        }

        log.info("Calculate with params: k={}, maxRadius={}m, iterations={}, nodes={}, edges={}",
                k, maxRadiusMeters, iterations, allNodes.size(), graph.edgeSet().size());

        List<RoadNode> bestStations = IntStream.range(0, iterations)
                .parallel()
                .mapToObj(_ -> runOnce(graph, allNodes, k, maxRadiusMeters))
                .min(Comparator.comparingInt(List::size))
                .orElse(List.of());

        Map<String, Double> nodeDistances = computeMinWeightedDistances(graph, allNodes, bestStations, maxRadiusMeters);

        log.info("K-coverage finished: selected {} charging stations (from {} iterations)", bestStations.size(), iterations);

        return new PlacementResult(bestStations, bestStations.size(), nodeDistances);
    }

    private List<RoadNode> runOnce(Graph<RoadNode, RoadEdge> graph, Set<RoadNode> allNodes, int k, double maxRadiusMeters) {
        Map<RoadNode, Integer> coverageCount = new HashMap<>();
        for (RoadNode node : allNodes) {
            coverageCount.put(node, 0);
        }

        Set<RoadNode> unsatisfied = new HashSet<>(allNodes);
        List<RoadNode> stations = new ArrayList<>();
        Set<RoadNode> stationSet = new HashSet<>();

        while (!unsatisfied.isEmpty()) {
            // Candidates: nepokryté uzly, ktoré ešte nie sú stanicou
            List<RoadNode> candidates = unsatisfied.stream()
                    .filter(n -> !stationSet.contains(n))
                    .toList();
            if (candidates.isEmpty()) break;

            RoadNode selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            stations.add(selected);
            stationSet.add(selected);

            Map<RoadNode, Double> reachable = dijkstraDistances(graph, selected, maxRadiusMeters);
            for (RoadNode node : reachable.keySet()) {
                int count = coverageCount.merge(node, 1, Integer::sum);
                if (count >= k) {
                    unsatisfied.remove(node);
                }
            }
        }

        return stations;
    }

    private Map<RoadNode, Double> dijkstraDistances(Graph<RoadNode, RoadEdge> graph, RoadNode source, double maxRadius) {
        Map<RoadNode, Double> dist = new HashMap<>();
        dist.put(source, 0.0);
        PriorityQueue<RoadNode> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        pq.add(source);

        while (!pq.isEmpty()) {
            RoadNode u = pq.poll();
            double du = dist.get(u);
            for (RoadEdge edge : graph.edgesOf(u)) {
                RoadNode v = getOpposite(graph, u, edge);
                double dv = du + graph.getEdgeWeight(edge);
                if (dv <= maxRadius && dv < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, dv);
                    pq.add(v);
                }
            }
        }

        return dist;
    }

    private Map<String, Double> computeMinWeightedDistances(
            Graph<RoadNode, RoadEdge> graph,
            Set<RoadNode> allNodes,
            List<RoadNode> stations,
            double maxRadiusMeters) {

        Map<String, Double> distances = new HashMap<>();
        for (RoadNode node : allNodes) {
            distances.put(node.getId(), Double.MAX_VALUE);
        }

        for (RoadNode station : stations) {
            Map<RoadNode, Double> stationDist = dijkstraDistances(graph, station, maxRadiusMeters);
            for (Map.Entry<RoadNode, Double> entry : stationDist.entrySet()) {
                String nodeId = entry.getKey().getId();
                if (entry.getValue() < distances.get(nodeId)) {
                    distances.put(nodeId, entry.getValue());
                }
            }
        }

        // Uzly mimo dosahu staníc dostanú -1
        distances.replaceAll((_, d) -> d == Double.MAX_VALUE ? -1.0 : d);

        return distances;
    }

    private RoadNode getOpposite(Graph<RoadNode, RoadEdge> graph, RoadNode node, RoadEdge edge) {
        RoadNode source = graph.getEdgeSource(edge);
        return source.equals(node) ? graph.getEdgeTarget(edge) : source;
    }
}
