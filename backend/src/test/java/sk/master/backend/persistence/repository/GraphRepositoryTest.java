package sk.master.backend.persistence.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import sk.master.backend.persistence.dto.GraphSummaryDto;
import sk.master.backend.persistence.entity.GraphEdgeEntity;
import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.entity.GraphMetricsEmbeddable;
import sk.master.backend.persistence.entity.GraphNodeEntity;
import sk.master.backend.persistence.entity.GraphStationEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class GraphRepositoryTest {

    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private TestEntityManager entityManager;

    private GraphEntity newGraph(String name, Long userId, int nodes, int edges, int stations) {
        GraphEntity entity = new GraphEntity();
        entity.setName(name);
        entity.setUserId(userId);

        for (int i = 0; i < nodes; i++) {
            entity.getNodes().add(new GraphNodeEntity("n" + i, 48.0 + i * 0.001, 17.0 + i * 0.001, 0));
        }
        for (int i = 0; i < edges; i++) {
            entity.getEdges().add(new GraphEdgeEntity("n" + i, "n" + (i + 1), 100.0));
        }
        for (int i = 0; i < stations; i++) {
            entity.getStations().add(new GraphStationEntity("s" + i, 48.0 + i * 0.002, 17.0 + i * 0.002, i + 1));
        }
        entity.setMetrics(new GraphMetricsEmbeddable(
                nodes, edges, 2.0 * edges / Math.max(nodes, 1), 400.0,
                0.0, 100.0, 50.0, 1, 200.0, 2));
        return entity;
    }

    @Test
    void saveAndLoad_persistsAllElementCollections() {
        GraphEntity input = newGraph("graph-1", 42L, 3, 2, 1);

        GraphEntity saved = graphRepository.save(input);
        entityManager.flush();
        entityManager.clear();

        Optional<GraphEntity> loaded = graphRepository.findByIdAndUserId(saved.getId(), 42L);

        assertTrue(loaded.isPresent(), "Graph must be reloadable for its owning user");
        GraphEntity g = loaded.get();
        assertNotNull(g.getCreatedAt(), "@PrePersist must populate createdAt");
        assertEquals(3, g.getNodes().size());
        assertEquals(2, g.getEdges().size());
        assertEquals(1, g.getStations().size());
        assertEquals("n0", g.getNodes().get(0).getNodeId());
        assertEquals(48.0, g.getNodes().get(0).getLat());
        assertEquals("n0", g.getEdges().get(0).getSourceId());
        assertEquals("s0", g.getStations().get(0).getStationId());
        assertEquals(3, g.getMetrics().getNodeCount());
    }

    @Test
    void findSummariesByUserId_returnsMatchingCountsAndExcludesOtherUsers() {
        graphRepository.save(newGraph("alice-1", 42L, 5, 4, 2));
        graphRepository.save(newGraph("alice-2", 42L, 3, 2, 0));
        graphRepository.save(newGraph("bob-1",   99L, 7, 6, 3));
        entityManager.flush();
        entityManager.clear();

        List<GraphSummaryDto> aliceSummaries = graphRepository.findSummariesByUserId(42L);

        assertEquals(2, aliceSummaries.size(), "Only Alice's graphs are returned");

        GraphSummaryDto first = aliceSummaries.stream()
                .filter(s -> s.name().equals("alice-1")).findFirst().orElseThrow();
        assertEquals(5, first.nodeCount());
        assertEquals(4, first.edgeCount());
        assertEquals(2, first.stationCount());

        GraphSummaryDto second = aliceSummaries.stream()
                .filter(s -> s.name().equals("alice-2")).findFirst().orElseThrow();
        assertEquals(3, second.nodeCount());
        assertEquals(2, second.edgeCount());
        assertEquals(0, second.stationCount());
    }
}
