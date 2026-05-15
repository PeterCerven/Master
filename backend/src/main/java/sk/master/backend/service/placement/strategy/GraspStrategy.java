package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Component
public class GraspStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(GraspStrategy.class);

    private record DistEntry(double dist, RoadNode node) {}
    private record TrialResult(List<Integer> stationIndices, boolean complete) {}

    @Override
    public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
        int k = params.getK();
        double maxRadius = params.getMaxRadiusMeters();
        int iterations = params.getIterations();
        double graspAlpha = params.getGraspAlpha();
        int graspEvalBudget = params.getGraspEvalBudget();
        Graph<RoadNode, RoadEdge> graph = roadGraph.getGraph();
        Set<RoadNode> allNodesSet = graph.vertexSet();

        if (allNodesSet.isEmpty()) {
            return new PlacementResult(List.of(), 0, Map.of());
        }

        int n = allNodesSet.size();
        log.info("GRASP: k={}, maxRadius={}m, iterations={}, alpha={}, evalBudget={}, nodes={}, edges={}",
                k, maxRadius, iterations, graspAlpha, graspEvalBudget, n, graph.edgeSet().size());

        // ---- 1. Build node index ----
        RoadNode[] indexToNode = allNodesSet.toArray(new RoadNode[0]);
        Map<RoadNode, Integer> nodeIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            nodeIndex.put(indexToNode[i], i);
        }

        // ---- 2. Pre-compute Dijkstra reachability ----
        long t0 = System.currentTimeMillis();
        BitSet[] reachable = new BitSet[n];
        IntStream.range(0, n).parallel().forEach(i ->
                reachable[i] = dijkstraReachable(graph, indexToNode[i], maxRadius, nodeIndex));
        log.info("Dijkstra reachability phase done in {} ms", System.currentTimeMillis() - t0);

        // ---- 3. Construction + removal trials in parallel ----
        AtomicInteger bestCount = new AtomicInteger(Integer.MAX_VALUE);
        long masterSeed = System.nanoTime();
        log.info("GRASP master seed = {}", masterSeed);
        long tConstruct = System.currentTimeMillis();

        List<Integer> bestAfterConstruct = IntStream.range(0, iterations)
                .parallel()
                .mapToObj(iter -> {
                    SplittableRandom rng = new SplittableRandom(masterSeed + iter);
                    TrialResult c = construct(reachable, n, k, graspAlpha, graspEvalBudget, bestCount, rng);
                    if (!c.complete()) return c;
                    List<Integer> stations = new ArrayList<>(c.stationIndices());
                    int[] cov = rebuildCoverage(reachable, n, stations);
                    BitSet stationSet = new BitSet(n);
                    for (int s : stations) stationSet.set(s);
                    removeRedundant(reachable, k, stations, stationSet, cov);
                    bestCount.updateAndGet(v -> Math.min(v, stations.size()));
                    return new TrialResult(stations, true);
                })
                .filter(TrialResult::complete)
                .map(TrialResult::stationIndices)
                .min(Comparator.comparingInt(List::size))
                .orElse(List.of());

        log.info("Construction+removal done: best = {} stations in {} ms",
                bestAfterConstruct.size(), System.currentTimeMillis() - tConstruct);

        // ---- 4. Single swap pass on the best solution ----
        long tSwap = System.currentTimeMillis();
        List<Integer> finalIndices = swapPass(reachable, n, k, bestAfterConstruct);
        log.info("Swap pass: {} -> {} stations in {} ms",
                bestAfterConstruct.size(), finalIndices.size(),
                System.currentTimeMillis() - tSwap);

        List<RoadNode> bestStations = new ArrayList<>(finalIndices.size());
        for (int idx : finalIndices) bestStations.add(indexToNode[idx]);

        Map<String, Double> nodeDistances = computeMinWeightedDistances(graph, allNodesSet, bestStations, maxRadius);

        log.info("GRASP finished: selected {} stations (from {} iterations) in {} ms",
                bestStations.size(), iterations, System.currentTimeMillis() - t0);

        return new PlacementResult(bestStations, bestStations.size(), nodeDistances);
    }

    // ============================================================
    // CONSTRUCTION (greedy randomized)
    // Gain computed via scratch BitSet: clear+or+and+cardinality
    // ============================================================
    private TrialResult construct(BitSet[] reachable, int n, int k,
                                  double graspAlpha, int graspEvalBudget,
                                  AtomicInteger bestCount, SplittableRandom rng) {
        int[] coverageCount = new int[n];
        BitSet unsatisfied = new BitSet(n);
        unsatisfied.set(0, n);
        int unsatisfiedRemaining = n;

        BitSet candidates = new BitSet(n);
        candidates.set(0, n);

        BitSet scratch = new BitSet(n);

        List<Integer> stations = new ArrayList<>();
        int[] candBuf = new int[n];
        int[] gains = new int[Math.min(graspEvalBudget, n)];

        while (unsatisfiedRemaining > 0) {
            int candCount = candidates.cardinality();
            if (candCount == 0) break;

            if (stations.size() >= bestCount.get()) {
                return new TrialResult(stations, false);
            }

            int idx = 0;
            for (int c = candidates.nextSetBit(0); c >= 0; c = candidates.nextSetBit(c + 1)) {
                candBuf[idx++] = c;
            }

            int evalSize = Math.min(graspEvalBudget, candCount);
            if (evalSize < candCount) {
                for (int i = 0; i < evalSize; i++) {
                    int j = i + rng.nextInt(candCount - i);
                    int tmp = candBuf[i]; candBuf[i] = candBuf[j]; candBuf[j] = tmp;
                }
            }

            int gmax = Integer.MIN_VALUE;
            int gmin = Integer.MAX_VALUE;
            for (int i = 0; i < evalSize; i++) {
                BitSet reach = reachable[candBuf[i]];
                scratch.clear();
                scratch.or(reach);
                scratch.and(unsatisfied);
                int g = scratch.cardinality();
                gains[i] = g;
                if (g > gmax) gmax = g;
                if (g < gmin) gmin = g;
            }

            int threshold = (gmax == gmin) ? gmax : (int) Math.ceil(gmax - graspAlpha * (gmax - gmin));
            int rclSize = 0;
            for (int i = 0; i < evalSize; i++) {
                if (gains[i] >= threshold) {
                    int tmp = candBuf[rclSize];
                    candBuf[rclSize] = candBuf[i];
                    candBuf[i] = tmp;
                    rclSize++;
                }
            }
            if (rclSize == 0) rclSize = evalSize;

            int selected = candBuf[rng.nextInt(rclSize)];
            stations.add(selected);
            candidates.clear(selected);

            BitSet reach = reachable[selected];
            for (int w = reach.nextSetBit(0); w >= 0; w = reach.nextSetBit(w + 1)) {
                int c = ++coverageCount[w];
                if (c == k) {
                    unsatisfied.clear(w);
                    unsatisfiedRemaining--;
                }
            }
        }

        return new TrialResult(stations, unsatisfiedRemaining == 0);
    }

    // ============================================================
    // POST-PROCESSING: SWAP runs ONCE on the best solution
    // ============================================================
    private List<Integer> swapPass(BitSet[] reachable, int n, int k, List<Integer> initial) {
        List<Integer> stations = new ArrayList<>(initial);
        BitSet stationSet = new BitSet(n);
        for (int s : stations) stationSet.set(s);
        int[] cov = rebuildCoverage(reachable, n, stations);

        BitSet critical = new BitSet(n);   // reused across stations

        boolean swapFound = true;
        while (swapFound) {
            swapFound = false;
            outer:
            for (int si = 0; si < stations.size(); si++) {
                int sOut = stations.get(si);
                BitSet reachOut = reachable[sOut];

                critical.clear();
                for (int w = reachOut.nextSetBit(0); w >= 0; w = reachOut.nextSetBit(w + 1)) {
                    if (cov[w] == k) critical.set(w);
                }

                for (int vIn = reachOut.nextSetBit(0); vIn >= 0; vIn = reachOut.nextSetBit(vIn + 1)) {
                    if (stationSet.get(vIn)) continue;
                    BitSet reachIn = reachable[vIn];


                    boolean feasible = true;
                    for (int w = critical.nextSetBit(0); w >= 0; w = critical.nextSetBit(w + 1)) {
                        if (!reachIn.get(w)) { feasible = false; break; }
                    }
                    if (!feasible) continue;

                    // Apply swap
                    stations.set(si, vIn);
                    stationSet.clear(sOut);
                    stationSet.set(vIn);
                    for (int w = reachOut.nextSetBit(0); w >= 0; w = reachOut.nextSetBit(w + 1)) cov[w]--;
                    for (int w = reachIn.nextSetBit(0); w >= 0; w = reachIn.nextSetBit(w + 1)) cov[w]++;

                    if (anyRedundant(reachable, k, stations, cov)) {
                        removeRedundant(reachable, k, stations, stationSet, cov);
                        swapFound = true;
                        break outer;
                    }

                    // Rollback
                    stations.set(si, sOut);
                    stationSet.clear(vIn);
                    stationSet.set(sOut);
                    for (int w = reachIn.nextSetBit(0); w >= 0; w = reachIn.nextSetBit(w + 1)) cov[w]--;
                    for (int w = reachOut.nextSetBit(0); w >= 0; w = reachOut.nextSetBit(w + 1)) cov[w]++;
                }
            }
        }

        return stations;
    }

    private int[] rebuildCoverage(BitSet[] reachable, int n, List<Integer> stations) {
        int[] cov = new int[n];
        for (int s : stations) {
            BitSet reach = reachable[s];
            for (int w = reach.nextSetBit(0); w >= 0; w = reach.nextSetBit(w + 1)) {
                cov[w]++;
            }
        }
        return cov;
    }

    private boolean anyRedundant(BitSet[] reachable, int k, List<Integer> stations, int[] cov) {
        for (int s : stations) {
            BitSet reach = reachable[s];
            boolean red = true;
            for (int w = reach.nextSetBit(0); w >= 0; w = reach.nextSetBit(w + 1)) {
                if (cov[w] < k + 1) { red = false; break; }
            }
            if (red) return true;
        }
        return false;
    }

    private void removeRedundant(BitSet[] reachable, int k,
                                 List<Integer> stations, BitSet stationSet, int[] cov) {
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < stations.size(); i++) {
                int s = stations.get(i);
                BitSet reach = reachable[s];
                boolean redundant = true;
                for (int w = reach.nextSetBit(0); w >= 0; w = reach.nextSetBit(w + 1)) {
                    if (cov[w] < k + 1) { redundant = false; break; }
                }
                if (redundant) {
                    stations.remove(i);
                    stationSet.clear(s);
                    for (int w = reach.nextSetBit(0); w >= 0; w = reach.nextSetBit(w + 1)) {
                        cov[w]--;
                    }
                    improved = true;
                    break;
                }
            }
        }
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