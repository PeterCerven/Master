package sk.master.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sk.master.backend.persistence.entity.GraphEntity;

@Repository
public interface GraphRepository extends JpaRepository<GraphEntity, Long> {
    GraphEntity findGraphEntityById(Long id);
}