package sk.master.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sk.master.backend.persistence.entity.GraphEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface GraphRepository extends JpaRepository<GraphEntity, Long> {
    List<GraphEntity> findAllByUserId(Long userId);
    Optional<GraphEntity> findByIdAndUserId(Long id, Long userId);
}