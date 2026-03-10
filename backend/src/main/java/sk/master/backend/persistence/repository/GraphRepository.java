package sk.master.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sk.master.backend.persistence.dto.GraphSummaryDto;
import sk.master.backend.persistence.entity.GraphEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface GraphRepository extends JpaRepository<GraphEntity, Long> {
    Optional<GraphEntity> findByIdAndUserId(Long id, Long userId);
    void deleteAllByUserId(Long userId);

    @Query("""
            SELECT new sk.master.backend.persistence.dto.GraphSummaryDto(
                g.id, g.name, g.createdAt, SIZE(g.nodes), SIZE(g.edges), SIZE(g.stations)
            )
            FROM GraphEntity g
            WHERE g.userId = :userId
            """)
    List<GraphSummaryDto> findSummariesByUserId(@Param("userId") Long userId);
}