package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;
import java.util.stream.IntStream;


@Component
public class GreedyStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(GreedyStrategy.class);

    private record GainEntry(int gain, int stamp, int nodeIdx) {}
    private record DistEntry(double dist, RoadNode node) {}

    @Override
    public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
        int k = params.getK();
        double maxRadiusMeters = params.getMaxRadiusMeters();
        Graph<RoadNode, RoadEdge> graph = roadGraph.getGraph();
        Set<RoadNode> allNodesSet = graph.vertexSet();

        if (allNodesSet.isEmpty()) {
            return new PlacementResult(List.of(), 0, Map.of());
        }

        int n = allNodesSet.size();
        log.info("Greedy k-coverage: k={}, maxRadius={}m, nodes={}, edges={}",
                k, maxRadiusMeters, n, graph.edgeSet().size());

        RoadNode[] indexToNode = allNodesSet.toArray(new RoadNode[0]);
        Map<RoadNode, Integer> nodeIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            nodeIndex.put(indexToNode[i], i);
        }

        long t0 = System.currentTimeMillis();
        BitSet[] reachable = new BitSet[n];
        IntStream.range(0, n).parallel().forEach(i ->
                reachable[i] = dijkstraReachable(graph, indexToNode[i], maxRadiusMeters, nodeIndex));
        log.info("Dijkstra reachability phase done in {} ms", System.currentTimeMillis() - t0);

        int[] coverageCount = new int[n];
        BitSet unsatisfied = new BitSet(n);
        unsatisfied.set(0, n);

        List<RoadNode> stations = new ArrayList<>();
        BitSet stationSet = new BitSet(n);

        PriorityQueue<GainEntry> heap =
                new PriorityQueue<>(Comparator.comparingInt((GainEntry e) -> -e.gain()));
        for (int i = 0; i < n; i++) {
            heap.add(new GainEntry(reachable[i].cardinality(), 0, i));
        }

        int iteration = 0;
        int unsatisfiedRemaining = n;

        while (unsatisfiedRemaining > 0) {
            GainEntry top = heap.poll();
            if (top == null) break;
            int candIdx = top.nodeIdx();
            if (stationSet.get(candIdx)) continue;

            if (top.stamp() < iteration) {
                BitSet reach = reachable[candIdx];
                int g = 0;
                for (int w = reach.nextSetBit(0); w >= 0; w = reach.nextSetBit(w + 1)) {
                    if (unsatisfied.get(w)) g++;
                }
                heap.add(new GainEntry(g, iteration, candIdx));
                continue;
            }

            if (top.gain() == 0) break;

            RoadNode candidate = indexToNode[candIdx];
            stations.add(candidate);
            stationSet.set(candIdx);

            BitSet reach = reachable[candIdx];
            for (int w = reach.nextSetBit(0); w >= 0; w = reach.nextSetBit(w + 1)) {
                int c = ++coverageCount[w];
                if (c == k) {
                    unsatisfied.clear(w);
                    unsatisfiedRemaining--;
                }
            }
            iteration++;
        }

        Map<String, Double> nodeDistances =
                computeMinWeightedDistances(graph, allNodesSet, stations, maxRadiusMeters);
        log.info("Greedy k-coverage finished: selected {} charging stations in {} ms total",
                stations.size(), System.currentTimeMillis() - t0);
        return new PlacementResult(stations, stations.size(), nodeDistances);
    }

    private BitSet dijkstraReachable(Graph<RoadNode, RoadEdge> graph, RoadNode source,
                                     double maxRadius, Map<RoadNode, Integer> nodeIndex) {
        Map<RoadNode, Double> dist = new HashMap<>();
        dist.put(source, 0.0);

        PriorityQueue<DistEntry> pq = new PriorityQueue<>(Comparator.comparingDouble(DistEntry::dist));
        pq.add(new DistEntry(0.0, source));

        Set<RoadNode> visited = new HashSet<>();
        BitSet result = new BitSet(nodeIndex.size());

        while (!pq.isEmpty()) {
            DistEntry e = pq.poll();
            RoadNode u = e.node();
            if (!visited.add(u)) continue;
            result.set(nodeIndex.get(u));
            double du = e.dist();

            for (RoadEdge edge : graph.edgesOf(u)) {
                RoadNode v = getOpposite(graph, u, edge);
                if (visited.contains(v)) continue;
                double dv = du + graph.getEdgeWeight(edge);
                if (dv <= maxRadius && dv < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, dv);
                    pq.add(new DistEntry(dv, v));
                }
            }
        }
        return result;
    }

    private Map<String, Double> computeMinWeightedDistances(
            Graph<RoadNode, RoadEdge> graph,
            Set<RoadNode> allNodes,
            List<RoadNode> stations,
            double maxRadius) {

        Map<RoadNode, Double> dist = new HashMap<>();
        PriorityQueue<DistEntry> pq = new PriorityQueue<>(Comparator.comparingDouble(DistEntry::dist));
        for (RoadNode station : stations) {
            dist.put(station, 0.0);
            pq.add(new DistEntry(0.0, station));
        }

        Set<RoadNode> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            DistEntry e = pq.poll();
            RoadNode u = e.node();
            if (!visited.add(u)) continue;
            double du = e.dist();

            for (RoadEdge edge : graph.edgesOf(u)) {
                RoadNode v = getOpposite(graph, u, edge);
                if (visited.contains(v)) continue;
                double dv = du + graph.getEdgeWeight(edge);
                if (dv <= maxRadius && dv < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, dv);
                    pq.add(new DistEntry(dv, v));
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