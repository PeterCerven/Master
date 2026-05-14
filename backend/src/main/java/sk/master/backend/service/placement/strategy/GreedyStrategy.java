package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class GreedyStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(GreedyStrategy.class);

    private record GainEntry(int gain, int stamp, RoadNode node) {}

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

        Map<RoadNode, Map<RoadNode, Double>> dijkstraCache = new ConcurrentHashMap<>();

        Map<RoadNode, Integer> coverageCount = new HashMap<>(allNodes.size());
        for (RoadNode node : allNodes) coverageCount.put(node, 0);

        Set<RoadNode> unsatisfied = new HashSet<>(allNodes);
        List<RoadNode> stations = new ArrayList<>();
        Set<RoadNode> stationSet = new HashSet<>();

        allNodes.parallelStream().forEach(n ->
                dijkstraCache.computeIfAbsent(n, src -> dijkstraDistances(graph, src, maxRadiusMeters)));

        PriorityQueue<GainEntry> heap =
                new PriorityQueue<>(Comparator.comparingInt((GainEntry e) -> -e.gain()));
        Map<RoadNode, Integer> latestStamp = new HashMap<>(allNodes.size());
        for (RoadNode n : allNodes) {
            int g = dijkstraCache.get(n).size();
            heap.add(new GainEntry(g, 0, n));
            latestStamp.put(n, 0);
        }

        int stamp = 0;
        while (!unsatisfied.isEmpty()) {
            GainEntry top = heap.poll();
            if (top == null) break;
            RoadNode candidate = top.node();
            if (stationSet.contains(candidate)) continue;

            if (top.stamp() != latestStamp.get(candidate)) {
                Map<RoadNode, Double> reachable = dijkstraCache.get(candidate);
                int g = 0;
                for (RoadNode w : reachable.keySet()) {
                    if (unsatisfied.contains(w)) g++;
                }
                int newStamp = ++stamp;
                latestStamp.put(candidate, newStamp);
                heap.add(new GainEntry(g, newStamp, candidate));
                continue;
            }

            if (top.gain() == 0) break;
            stations.add(candidate);
            stationSet.add(candidate);
            for (RoadNode w : dijkstraCache.get(candidate).keySet()) {
                int c = coverageCount.merge(w, 1, Integer::sum);
                if (c >= k) unsatisfied.remove(w);
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
            double maxRadius) {

        Map<RoadNode, Double> dist = new HashMap<>();
        for (RoadNode station : stations) dist.put(station, 0.0);

        PriorityQueue<RoadNode> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        pq.addAll(stations);
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

        Map<String, Double> result = new HashMap<>(allNodes.size());
        for (RoadNode node : allNodes) {
            result.put(node.getId(), dist.getOrDefault(node, -1.0));
        }
        return result;
    }

    private RoadNode getOpposite(Graph<RoadNode, RoadEdge> graph, RoadNode node, RoadEdge edge) {
        RoadNode source = graph.getEdgeSource(edge);
        return source.equals(node) ? graph.getEdgeTarget(edge) : source;
    }
}
