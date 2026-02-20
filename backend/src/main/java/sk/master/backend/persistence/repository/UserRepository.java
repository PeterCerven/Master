package sk.master.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sk.master.backend.persistence.entity.PipelineConfigEntity;

@Repository
public interface UserRepository extends JpaRepository<PipelineConfigEntity, Long> {
}
