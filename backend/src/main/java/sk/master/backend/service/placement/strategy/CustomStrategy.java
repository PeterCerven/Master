package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Component
public class CustomStrategy implements PlacementStrategy {

private static final Logger log = LoggerFactory.getLogger(CustomStrategy.class);
private static final double ALPHA = 0.3; // TODO pridat ako config
private static final int EVAL_BUDGET = 500; // TODO pridat ako config

@Override
public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
    int k = params.getK();
    double maxRadius = params.getMaxRadiusMeters();
    int iterations = params.getIterations();
    Graph<RoadNode, RoadEdge> graph = roadGraph.getGraph();
    List<RoadNode> allNodes = new ArrayList<>(graph.vertexSet());

    if (allNodes.isEmpty()) {
        return new PlacementResult(List.of(), 0, Map.of());
    }

    log.info("GRASP: k={}, maxRadius={}m, iterations={}, nodes={}, edges={}",
            k, maxRadius, iterations, allNodes.size(), graph.edgeSet().size());

    ConcurrentHashMap<RoadNode, Map<RoadNode, Double>> dijkstraCache = new ConcurrentHashMap<>();
    AtomicInteger bestCount = new AtomicInteger(Integer.MAX_VALUE);
    List<RoadNode> bestSolution = IntStream.range(0, iterations)
            .parallel()
            .mapToObj(_ -> {
                List<RoadNode> constructed = greedyRandomizedConstruction(graph, allNodes, k, maxRadius, dijkstraCache, bestCount);
                List<RoadNode> sol = localSearch(graph, allNodes, constructed, k, maxRadius, dijkstraCache);
                bestCount.updateAndGet(v -> Math.min(v, sol.size()));
                return sol;
            })
            .min(Comparator.comparingInt(List::size))
            .orElse(List.of());

    Map<String, Double> nodeDistances = computeMinWeightedDistances(graph, allNodes, bestSolution, maxRadius);

    log.info("GRASP finished: selected {} stations (from {} iterations)", bestSolution.size(), iterations);

    return new PlacementResult(bestSolution, bestSolution.size(), nodeDistances);
}

private List<RoadNode> greedyRandomizedConstruction(
        Graph<RoadNode, RoadEdge> graph, List<RoadNode> allNodes,
        int k, double maxRadius, Map<RoadNode, Map<RoadNode, Double>> dijkstraCache,
        AtomicInteger bestCount) {

    Map<RoadNode, Integer> coverageCount = new HashMap<>();
    for (RoadNode node : allNodes) {
        coverageCount.put(node, 0);
    }

    Set<RoadNode> unsatisfied = new HashSet<>(allNodes);
    List<RoadNode> stations = new ArrayList<>();
    Set<RoadNode> candidates = new HashSet<>(allNodes);

    while (!unsatisfied.isEmpty()) {
        if (candidates.isEmpty()) break;

        // Subsample candidates for gain evaluation
        List<RoadNode> evalSet;
        if (candidates.size() > EVAL_BUDGET) {
            evalSet = new ArrayList<>(candidates);
            Collections.shuffle(evalSet, ThreadLocalRandom.current());
            evalSet = evalSet.subList(0, EVAL_BUDGET);
        } else {
            evalSet = new ArrayList<>(candidates);
        }

        // Compute gain for each candidate in evalSet
        int gmax = Integer.MIN_VALUE;
        int gmin = Integer.MAX_VALUE;
        Map<RoadNode, Integer> gain = new HashMap<>();
        for (RoadNode c : evalSet) {
            Map<RoadNode, Double> reachable = dijkstraCache.computeIfAbsent(c, n -> dijkstraDistances(graph, n, maxRadius));
            int g = 0;
            for (RoadNode w : reachable.keySet()) {
                if (unsatisfied.contains(w)) g++;
            }
            gain.put(c, g);
            if (g > gmax) gmax = g;
            if (g < gmin) gmin = g;
        }

        // Build RCL
        int threshold = (gmax == gmin) ? gmax : (int) Math.ceil(gmax - ALPHA * (gmax - gmin));
        List<RoadNode> rcl = new ArrayList<>();
        for (RoadNode c : evalSet) {
            if (gain.get(c) >= threshold) rcl.add(c);
        }
        if (rcl.isEmpty()) rcl.addAll(evalSet);

        RoadNode selected = rcl.get(ThreadLocalRandom.current().nextInt(rcl.size()));
        stations.add(selected);
        candidates.remove(selected);
        if (stations.size() >= bestCount.get()) return stations; // pruning: can't improve

        Map<RoadNode, Double> reachable = dijkstraCache.computeIfAbsent(selected, n -> dijkstraDistances(graph, n, maxRadius));
        for (RoadNode w : reachable.keySet()) {
            int count = coverageCount.merge(w, 1, Integer::sum);
            if (count >= k) {
                unsatisfied.remove(w);
            }
        }
    }

    return stations;
}

private List<RoadNode> localSearch(
        Graph<RoadNode, RoadEdge> graph, List<RoadNode> allNodes,
        List<RoadNode> initialStations, int k, double maxRadius,
        Map<RoadNode, Map<RoadNode, Double>> dijkstraCache) {

    List<RoadNode> stations = new ArrayList<>(initialStations);
    Set<RoadNode> stationSet = new HashSet<>(stations);

    // Rebuild coverage from scratch
    Map<RoadNode, Integer> coverageCount = new HashMap<>();
    for (RoadNode node : allNodes) coverageCount.put(node, 0);
    for (RoadNode s : stations) {
        Map<RoadNode, Double> reachable = dijkstraCache.computeIfAbsent(s, n -> dijkstraDistances(graph, n, maxRadius));
        for (RoadNode w : reachable.keySet()) {
            coverageCount.merge(w, 1, Integer::sum);
        }
    }

    // Removal phase: repeat until no redundant station found
    boolean improved = true;
    while (improved) {
        improved = false;
        for (int i = 0; i < stations.size(); i++) {
            RoadNode s = stations.get(i);
            Map<RoadNode, Double> reachable = dijkstraCache.computeIfAbsent(s, n -> dijkstraDistances(graph, n, maxRadius));
            boolean redundant = true;
            for (RoadNode w : reachable.keySet()) {
                if (coverageCount.getOrDefault(w, 0) < k + 1) {
                    redundant = false;
                    break;
                }
            }
            if (redundant) {
                stations.remove(i);
                stationSet.remove(s);
                for (RoadNode w : reachable.keySet()) {
                    coverageCount.merge(w, -1, Integer::sum);
                }
                improved = true;
                break;
            }
        }
    }

    // Swap phase: try swaps that enable a subsequent removal (net -1 stations)
    boolean swapFound = true;
    while (swapFound) {
        swapFound = false;
        outer:
        for (int si = 0; si < stations.size(); si++) {
            RoadNode sOut = stations.get(si);
            Map<RoadNode, Double> reachableOut = dijkstraCache.computeIfAbsent(sOut, n -> dijkstraDistances(graph, n, maxRadius));

            for (RoadNode vIn : reachableOut.keySet()) {
                if (stationSet.contains(vIn)) continue;
                Map<RoadNode, Double> reachableIn = dijkstraCache.computeIfAbsent(vIn, n -> dijkstraDistances(graph, n, maxRadius));

                // Check feasibility: nodes covered only by sOut must be covered by vIn after swap
                boolean feasible = true;
                for (RoadNode w : reachableOut.keySet()) {
                    if (!reachableIn.containsKey(w) && coverageCount.getOrDefault(w, 0) - 1 < k) {
                        feasible = false;
                        break;
                    }
                }
                if (!feasible) continue;

                // Apply swap
                stations.set(si, vIn);
                stationSet.remove(sOut);
                stationSet.add(vIn);
                for (RoadNode w : reachableOut.keySet()) coverageCount.merge(w, -1, Integer::sum);
                for (RoadNode w : reachableIn.keySet()) coverageCount.merge(w, 1, Integer::sum);

                // Check if any station is now removable
                boolean removable = false;
                for (RoadNode s : stations) {
                    Map<RoadNode, Double> reachableS = dijkstraCache.computeIfAbsent(s, n -> dijkstraDistances(graph, n, maxRadius));
                    boolean red = true;
                    for (RoadNode w : reachableS.keySet()) {
                        if (coverageCount.getOrDefault(w, 0) < k + 1) { red = false; break; }
                    }
                    if (red) { removable = true; break; }
                }

                if (removable) {
                    // Remove all redundant stations
                    boolean rem = true;
                    while (rem) {
                        rem = false;
                        for (int j = 0; j < stations.size(); j++) {
                            RoadNode s = stations.get(j);
                            Map<RoadNode, Double> rs = dijkstraCache.computeIfAbsent(s, n -> dijkstraDistances(graph, n, maxRadius));
                            boolean red = true;
                            for (RoadNode w : rs.keySet()) {
                                if (coverageCount.getOrDefault(w, 0) < k + 1) { red = false; break; }
                            }
                            if (red) {
                                stations.remove(j);
                                stationSet.remove(s);
                                for (RoadNode w : rs.keySet()) coverageCount.merge(w, -1, Integer::sum);
                                rem = true;
                                break;
                            }
                        }
                    }
                    swapFound = true;
                    break outer;
                } else {
                    // Rollback swap
                    stations.set(si, sOut);
                    stationSet.remove(vIn);
                    stationSet.add(sOut);
                    for (RoadNode w : reachableIn.keySet()) coverageCount.merge(w, -1, Integer::sum);
                    for (RoadNode w : reachableOut.keySet()) coverageCount.merge(w, 1, Integer::sum);
                }
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
        Graph<RoadNode, RoadEdge> graph, List<RoadNode> allNodes,
        List<RoadNode> stations, double maxRadius) {

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

    Map<String, Double> result = new HashMap<>();
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
