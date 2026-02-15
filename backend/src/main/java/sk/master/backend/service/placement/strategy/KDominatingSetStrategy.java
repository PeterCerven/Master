package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;

/**
 * Náhodný algoritmus pre k-dominujúcu množinu.
 * <p>
 * Nájde množinu S uzlov tak, že každý uzol v grafe
 * je do k skokov od aspoň jedného uzla v S.
 * <p>
 * Algoritmus:
 * 1. Pre každý uzol vypočíta k-skokové okolie cez BFS s limitom hĺbky.
 * 2. Kým existujú nepokryté uzly, náhodne vyberie uzol z nepokrytých.
 * 3. Pridá ho do dominujúcej množiny a označí jeho okolie ako pokryté.
 */
@Component
public class KDominatingSetStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(KDominatingSetStrategy.class);
    private final Random random = new Random();

    @Override
    public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
        int k = params.getK();
        Graph<RoadNode, RoadEdge> graph = roadGraph.getGraph();
        Set<RoadNode> allNodes = graph.vertexSet();

        if (allNodes.isEmpty()) {
            return new PlacementResult(List.of(), 0, Map.of());
        }

        log.info("Výpočet k-dominujúcej množiny: k={}, uzlov={}, hrán={}",
                k, allNodes.size(), graph.edgeSet().size());

        // 1. Náhodný výber z nepokrytých uzlov
        List<RoadNode> uncovered = new ArrayList<>(allNodes);
        Set<RoadNode> uncoveredSet = new HashSet<>(allNodes);
        List<RoadNode> dominatingSet = new ArrayList<>();

        while (!uncoveredSet.isEmpty()) {
            // Náhodne vybrať uzol z nepokrytých
            RoadNode selected = uncovered.get(random.nextInt(uncovered.size()));

            dominatingSet.add(selected);
            Set<RoadNode> neighborhood = computeKHopNeighborhood(graph, selected, k);
            uncoveredSet.removeAll(neighborhood);
            uncovered.removeAll(neighborhood);

            log.debug("Vybraný uzol {}: okolie={}, zostáva nepokrytých={}",
                    selected.getId(), neighborhood.size(), uncoveredSet.size());
        }

        // 3. Vypočítať vzdialenosti od každého uzla k najbližšej stanici (počet skokov)
        Map<String, Double> nodeDistances = computeMinHopDistances(graph, allNodes, dominatingSet, k);

        log.info("K-dominujúca množina dokončená: vybraných {} staníc", dominatingSet.size());

        return new PlacementResult(dominatingSet, dominatingSet.size(), nodeDistances);
    }

    private Set<RoadNode> computeKHopNeighborhood(Graph<RoadNode, RoadEdge> graph, RoadNode source, int k) {
        Set<RoadNode> visited = new HashSet<>();
        Queue<RoadNode> currentLevel = new LinkedList<>();
        currentLevel.add(source);
        visited.add(source);

        for (int depth = 0; depth < k; depth++) {
            Queue<RoadNode> nextLevel = new LinkedList<>();
            while (!currentLevel.isEmpty()) {
                RoadNode current = currentLevel.poll();
                for (RoadEdge edge : graph.edgesOf(current)) {
                    RoadNode neighbor = getOpposite(graph, current, edge);
                    if (visited.add(neighbor)) {
                        nextLevel.add(neighbor);
                    }
                }
            }
            currentLevel = nextLevel;
        }

        return visited;
    }

    private Map<String, Double> computeMinHopDistances(
            Graph<RoadNode, RoadEdge> graph,
            Set<RoadNode> allNodes,
            List<RoadNode> stations,
            int k) {

        Map<String, Double> distances = new HashMap<>();
        for (RoadNode node : allNodes) {
            distances.put(node.getId(), (double) (k + 1)); // inicializácia na max
        }

        for (RoadNode station : stations) {
            // BFS od každej stanice
            Map<RoadNode, Integer> hopDist = bfsDistances(graph, station, k);
            for (Map.Entry<RoadNode, Integer> entry : hopDist.entrySet()) {
                String nodeId = entry.getKey().getId();
                double currentMin = distances.get(nodeId);
                if (entry.getValue() < currentMin) {
                    distances.put(nodeId, (double) entry.getValue());
                }
            }
        }

        return distances;
    }

    private Map<RoadNode, Integer> bfsDistances(Graph<RoadNode, RoadEdge> graph, RoadNode source, int maxDepth) {
        Map<RoadNode, Integer> dist = new HashMap<>();
        Queue<RoadNode> currentLevel = new LinkedList<>();
        currentLevel.add(source);
        dist.put(source, 0);

        for (int depth = 0; depth < maxDepth; depth++) {
            Queue<RoadNode> nextLevel = new LinkedList<>();
            while (!currentLevel.isEmpty()) {
                RoadNode current = currentLevel.poll();
                for (RoadEdge edge : graph.edgesOf(current)) {
                    RoadNode neighbor = getOpposite(graph, current, edge);
                    if (!dist.containsKey(neighbor)) {
                        dist.put(neighbor, depth + 1);
                        nextLevel.add(neighbor);
                    }
                }
            }
            currentLevel = nextLevel;
        }

        return dist;
    }

    private RoadNode getOpposite(Graph<RoadNode, RoadEdge> graph, RoadNode node, RoadEdge edge) {
        RoadNode source = graph.getEdgeSource(edge);
        return source.equals(node) ? graph.getEdgeTarget(edge) : source;
    }
}
