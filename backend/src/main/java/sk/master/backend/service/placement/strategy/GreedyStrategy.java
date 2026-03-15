package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;


@Component
public class GreedyStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(GreedyStrategy.class);

    @Override
    public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
        int k = params.getK();
        double maxRadiusMeters = params.getMaxRadiusMeters();
        Graph<RoadNode, RoadEdge> graph = roadGraph.getGraph();
        Set<RoadNode> allNodes = graph.vertexSet();

        if (allNodes.isEmpty()) {
            return new PlacementResult(List.of(), 0, Map.of());
        }

        log.info("Greedy k-coverage: k={}, maxRadius={}m, nodes={}, edges={}",
                k, maxRadiusMeters, allNodes.size(), graph.edgeSet().size());

        Map<RoadNode, Integer> coverageCount = new HashMap<>();
        for (RoadNode node : allNodes) coverageCount.put(node, 0);

        Set<RoadNode> unsatisfied = new HashSet<>(allNodes);
        List<RoadNode> stations = new ArrayList<>();
        Set<RoadNode> stationSet = new HashSet<>();

        while (!unsatisfied.isEmpty()) {
            RoadNode best = null;
            int bestGain = -1;
            Map<RoadNode, Double> bestReachable = null;

            for (RoadNode candidate : unsatisfied) {
                if (stationSet.contains(candidate)) continue;
                Map<RoadNode, Double> reachable = dijkstraDistances(graph, candidate, maxRadiusMeters);
                int gain = (int) reachable.keySet().stream().filter(unsatisfied::contains).count();
                if (gain > bestGain) {
                    bestGain = gain;
                    best = candidate;
                    bestReachable = reachable;
                }
            }

            if (best == null) break;

            stations.add(best);
            stationSet.add(best);

            for (RoadNode node : bestReachable.keySet()) {
                int count = coverageCount.merge(node, 1, Integer::sum);
                if (count >= k) unsatisfied.remove(node);
            }
        }

        Map<String, Double> nodeDistances = computeMinWeightedDistances(graph, allNodes, stations, maxRadiusMeters);
        log.info("Greedy k-coverage finished: selected {} charging stations", stations.size());
        return new PlacementResult(stations, stations.size(), nodeDistances);
    }

    private Map<RoadNode, Double> dijkstraDistances(Graph<RoadNode, RoadEdge> graph, RoadNode source, double maxRadius) {
        Map<RoadNode, Double> dist = new HashMap<>();
        dist.put(source, 0.0);
        PriorityQueue<RoadNode> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        pq.add(source);
        Set<RoadNode> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            RoadNode u = pq.poll();
            if (!visited.add(u)) continue;
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

        distances.replaceAll((_, d) -> d == Double.MAX_VALUE ? -1.0 : d);

        return distances;
    }

    private RoadNode getOpposite(Graph<RoadNode, RoadEdge> graph, RoadNode node, RoadEdge edge) {
        RoadNode source = graph.getEdgeSource(edge);
        return source.equals(node) ? graph.getEdgeTarget(edge) : source;
    }
}
