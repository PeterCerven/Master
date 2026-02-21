package sk.master.backend.service.placement.strategy;

import org.jgrapht.Graph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.model.*;

import java.util.*;

/**
 * Gonzalezov algoritmus farthest-first traversal pre k-centrum.
 * <p>
 * Nájde k uzlov, ktoré minimalizujú maximálnu vzdialenosť od ľubovoľného
 * uzla k najbližšiemu centru. Používa váhy hrán (vzdialenosť v metroch).
 * <p>
 * Garantovaná 2-aproximácia optimálneho riešenia.
 * <p>
 * Algoritmus:
 * 1. Vyber počiatočný uzol (uzol s najväčším stupňom).
 * 2. Opakuj k-1 krát: vyber uzol s maximálnou vzdialenosťou k najbližšiemu centru.
 * 3. Výsledok: k centier s minimálnou maximálnou vzdialenosťou.
 */
@Component
public class KCentreStrategy implements PlacementStrategy {

    private static final Logger log = LoggerFactory.getLogger(KCentreStrategy.class);

    @Override
    public PlacementResult computePlacement(RoadGraph roadGraph, PlacementParams params) {
        int k = params.getK();
        Graph<RoadNode, RoadEdge> graph = roadGraph.getGraph();
        Set<RoadNode> allNodes = graph.vertexSet();

        if (allNodes.isEmpty()) {
            return new PlacementResult(List.of(), 0, Map.of());
        }

        if (k >= allNodes.size()) {
            // Každý uzol je centrum
            List<RoadNode> all = new ArrayList<>(allNodes);
            Map<String, Double> distances = new HashMap<>();
            for (RoadNode node : allNodes) {
                distances.put(node.getId(), 0.0);
            }
            return new PlacementResult(all, 0.0, distances);
        }

        log.info("Výpočet k-centra: k={}, uzlov={}, hrán={}",
                k, allNodes.size(), graph.edgeSet().size());

        DijkstraShortestPath<RoadNode, RoadEdge> dijkstra = new DijkstraShortestPath<>(graph);

        // Inicializácia vzdialeností na nekonečno
        Map<RoadNode, Double> minDistToCentre = new HashMap<>();
        for (RoadNode node : allNodes) {
            minDistToCentre.put(node, Double.MAX_VALUE);
        }

        List<RoadNode> centres = new ArrayList<>();

        // 1. Vyber počiatočný uzol — uzol s najväčším stupňom
        RoadNode initial = selectInitialNode(graph, allNodes);
        centres.add(initial);
        updateDistances(dijkstra, initial, allNodes, minDistToCentre);

        log.debug("Počiatočné centrum: {} (stupeň={})", initial.getId(), graph.degreeOf(initial));

        // 2. Iteratívne vyber najvzdialenejší uzol
        for (int i = 1; i < k; i++) {
            RoadNode farthest = findFarthestNode(minDistToCentre, centres);
            if (farthest == null) break;

            centres.add(farthest);
            updateDistances(dijkstra, farthest, allNodes, minDistToCentre);

            log.debug("Centrum #{}: {}, vzdialenosť k najbližšiemu centru = {} m",
                    i + 1, farthest.getId(), minDistToCentre.get(farthest));
        }

        // 3. Objektívna hodnota = maximálna zostávajúca vzdialenosť
        double objectiveValue = 0;
        Map<String, Double> nodeDistances = new HashMap<>();
        for (Map.Entry<RoadNode, Double> entry : minDistToCentre.entrySet()) {
            double dist = entry.getValue() == Double.MAX_VALUE ? -1 : entry.getValue();
            nodeDistances.put(entry.getKey().getId(), dist);
            if (dist > objectiveValue) {
                objectiveValue = dist;
            }
        }

        log.info("K-center finished, number of centers: {} , max distance = {} m", centres.size(), objectiveValue);

        return new PlacementResult(centres, objectiveValue, nodeDistances);
    }

    private RoadNode selectInitialNode(Graph<RoadNode, RoadEdge> graph, Set<RoadNode> nodes) {
        RoadNode best = null;
        int maxDegree = -1;
        for (RoadNode node : nodes) {
            int degree = graph.degreeOf(node);
            if (degree > maxDegree) {
                maxDegree = degree;
                best = node;
            }
        }
        return best;
    }

    private void updateDistances(
            DijkstraShortestPath<RoadNode, RoadEdge> dijkstra,
            RoadNode newCentre,
            Set<RoadNode> allNodes,
            Map<RoadNode, Double> minDistToCentre) {

        ShortestPathAlgorithm.SingleSourcePaths<RoadNode, RoadEdge> paths = dijkstra.getPaths(newCentre);

        for (RoadNode node : allNodes) {
            double dist = paths.getWeight(node);
            if (dist < minDistToCentre.get(node)) {
                minDistToCentre.put(node, dist);
            }
        }
    }

    private RoadNode findFarthestNode(Map<RoadNode, Double> minDistToCentre, List<RoadNode> centres) {
        RoadNode farthest = null;
        double maxDist = -1;
        Set<RoadNode> centreSet = new HashSet<>(centres);

        for (Map.Entry<RoadNode, Double> entry : minDistToCentre.entrySet()) {
            if (centreSet.contains(entry.getKey())) continue;
            if (entry.getValue() > maxDist) {
                maxDist = entry.getValue();
                farthest = entry.getKey();
            }
        }

        return farthest;
    }
}
