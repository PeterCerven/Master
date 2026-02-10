package sk.master.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.entity.PipelineConfigEntity;
import sk.master.backend.persistence.repository.PipelineConfigRepository;
import sk.master.backend.service.PipelineConfigServiceImpl;

@Component
public class PipelineConfigSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfigSeeder.class);

    private final PipelineConfigRepository repository;

    public PipelineConfigSeeder(PipelineConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.countByUserIdIsNull() == 0) {
            log.info("No pipeline configuration found — creating defaults...");
            PipelineConfigEntity entity = new PipelineConfigEntity();
            PipelineConfigServiceImpl.applyDefaults(entity);
            repository.save(entity);
            log.info("Default pipeline configuration created.");
        } else {
            log.debug("Pipeline configuration already exists, skipping seed.");
        }
    }
}
