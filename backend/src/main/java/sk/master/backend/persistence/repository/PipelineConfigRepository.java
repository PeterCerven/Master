package sk.master.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sk.master.backend.persistence.entity.PipelineConfigEntity;

import java.util.Optional;

@Repository
public interface PipelineConfigRepository extends JpaRepository<PipelineConfigEntity, Long> {

    Optional<PipelineConfigEntity> findByUserIdIsNullAndActiveTrue();

    long countByUserIdIsNull();
}
