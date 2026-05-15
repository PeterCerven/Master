package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;


@Component
public class RandomStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(RandomStrategy.class);

    private record DistEntry(double dist, RoadNode node) {}
    private record TrialResult(List<RoadNode> stations, boolean complete) {}

    @Override
    public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
        int k = params.getK();
        double maxRadiusMeters = params.getMaxRadiusMeters();
        int iterations = params.getIterations();
        Graph<RoadNode, RoadEdge> graph = roadGraph.getGraph();
        Set<RoadNode> allNodesSet = graph.vertexSet();

        if (allNodesSet.isEmpty()) {
            return new PlacementResult(List.of(), 0, Map.of());
        }

        int n = allNodesSet.size();
        log.info("Random k-coverage: k={}, maxRadius={}m, iterations={}, nodes={}, edges={}",
                k, maxRadiusMeters, iterations, n, graph.edgeSet().size());

        // ---- 1. Build node index ----
        RoadNode[] indexToNode = allNodesSet.toArray(new RoadNode[0]);
        Map<RoadNode, Integer> nodeIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            nodeIndex.put(indexToNode[i], i);
        }

        ConcurrentHashMap<Integer, BitSet> reachableCache = new ConcurrentHashMap<>();
        AtomicInteger bestCount = new AtomicInteger(Integer.MAX_VALUE);

        long masterSeed = System.nanoTime();
        log.info("Random master seed = {} (save this value to reproduce results)", masterSeed);

        long t0 = System.currentTimeMillis();

        List<RoadNode> bestStations = IntStream.range(0, iterations)
                .parallel()
                .mapToObj(iter -> runOnce(graph, indexToNode, nodeIndex, n, k, maxRadiusMeters,
                        bestCount, reachableCache, masterSeed + iter))
                .filter(TrialResult::complete)            // <-- discard pruned incomplete trials
                .map(TrialResult::stations)
                .min(Comparator.comparingInt(List::size))
                .orElse(List.of());

        Map<String, Double> nodeDistances =
                computeMinWeightedDistances(graph, allNodesSet, bestStations, maxRadiusMeters);

        log.info("Random k-coverage finished: selected {} stations (from {} iterations) in {} ms",
                bestStations.size(), iterations, System.currentTimeMillis() - t0);

        return new PlacementResult(bestStations, bestStations.size(), nodeDistances);
    }

    private TrialResult runOnce(Graph<RoadNode, RoadEdge> graph,
                                RoadNode[] indexToNode, Map<RoadNode, Integer> nodeIndex, int n,
                                int k, double maxRadiusMeters, AtomicInteger bestCount,
                                ConcurrentHashMap<Integer, BitSet> reachableCache, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);

        int[] coverageCount = new int[n];
        BitSet unsatisfied = new BitSet(n);
        unsatisfied.set(0, n);
        int unsatisfiedRemaining = n;

        CandidatePool candidates = new CandidatePool(n);

        List<RoadNode> stations = new ArrayList<>();

        while (unsatisfiedRemaining > 0 && !candidates.isEmpty()) {
            int pickedIdx = candidates.pickRandom(rng);
            candidates.remove(pickedIdx);
            stations.add(indexToNode[pickedIdx]);

            if (stations.size() >= bestCount.get()) {
                return new TrialResult(stations, false);
            }

            BitSet reach = reachableCache.computeIfAbsent(pickedIdx,
                    idx -> dijkstraReachable(graph, indexToNode[idx], maxRadiusMeters, nodeIndex));

            for (int w = reach.nextSetBit(0); w >= 0; w = reach.nextSetBit(w + 1)) {
                int c = ++coverageCount[w];
                if (c == k) {
                    unsatisfied.clear(w);
                    unsatisfiedRemaining--;
                    candidates.remove(w);
                }
            }
        }

        boolean complete = unsatisfiedRemaining == 0;
        if (complete) {
            bestCount.updateAndGet(c -> Math.min(c, stations.size()));
        }
        return new TrialResult(stations, complete);
    }

    private BitSet dijkstraReachable(Graph<RoadNode, RoadEdge> graph, RoadNode source,
                                     double maxRadius, Map<RoadNode, Integer> nodeIndex) {
        Map<RoadNode, Double> dist = new HashMap<>();
        dist.put(source, 0.0);

        PriorityQueue<DistEntry> pq =
                new PriorityQueue<>(Comparator.comparingDouble(DistEntry::dist));
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
        PriorityQueue<DistEntry> pq =
                new PriorityQueue<>(Comparator.comparingDouble(DistEntry::dist));
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

    private static final class CandidatePool {
        private final int[] arr;
        private final int[] pos;
        private final boolean[] inPool;
        private int count;

        CandidatePool(int n) {
            arr = new int[n];
            pos = new int[n];
            inPool = new boolean[n];
            for (int i = 0; i < n; i++) {
                arr[i] = i;
                pos[i] = i;
                inPool[i] = true;
            }
            count = n;
        }

        int pickRandom(SplittableRandom rng) {
            return arr[rng.nextInt(count)];
        }

        void remove(int idx) {
            if (!inPool[idx]) return;          // idempotent: safe to call twice
            int idxPos = pos[idx];
            int last = arr[count - 1];
            arr[idxPos] = last;
            pos[last] = idxPos;
            inPool[idx] = false;
            count--;
        }

        boolean isEmpty() {
            return count == 0;
        }
    }
}