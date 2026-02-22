package sk.master.backend.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.master.backend.persistence.dto.PipelineConfigDto;
import sk.master.backend.persistence.entity.PipelineConfigEntity;
import sk.master.backend.persistence.model.PipelineConfig;
import sk.master.backend.persistence.repository.PipelineConfigRepository;

@Service
public class PipelineConfigServiceImpl implements PipelineConfigService {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfigServiceImpl.class);

    private final PipelineConfigRepository repository;

    public PipelineConfigServiceImpl(PipelineConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public PipelineConfigDto getActiveConfig() {
        PipelineConfigEntity entity = repository.findByUserIdIsNullAndActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active pipeline configuration found"));
        return toDto(entity);
    }

    @Override
    @Transactional
    public PipelineConfigDto updateConfig(PipelineConfigDto dto) {
        PipelineConfigEntity entity = repository.findByUserIdIsNullAndActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active pipeline configuration found"));

        updateEntityFromDto(entity, dto);
        entity = repository.save(entity);
        log.info("Configuration updated: id={}", entity.getId());
        return toDto(entity);
    }

    @Override
    @Transactional
    public PipelineConfigDto resetToDefaults() {
        PipelineConfigEntity entity = repository.findByUserIdIsNullAndActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active pipeline configuration found"));

        applyDefaults(entity);
        entity = repository.save(entity);
        log.info("Configuration reset to defaults: id={}", entity.getId());
        return toDto(entity);
    }

    @Override
    public PipelineConfig getActivePipelineConfig() {
        PipelineConfigEntity entity = repository.findByUserIdIsNullAndActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active pipeline configuration found"));
        return toPipelineConfig(entity);
    }

    // ====== Mapping methods ======

    private PipelineConfigDto toDto(PipelineConfigEntity e) {
        return new PipelineConfigDto(
                e.getId(), e.getName(),
                e.getMaxSpeedKmh(),
                e.getH3DedupResolution()
        );
    }

    private void updateEntityFromDto(PipelineConfigEntity e, PipelineConfigDto d) {
        if (d.getName() != null) e.setName(d.getName());
        e.setMaxSpeedKmh(d.getMaxSpeedKmh());
        e.setH3DedupResolution(d.getH3DedupResolution());
    }

    private PipelineConfig toPipelineConfig(PipelineConfigEntity e) {
        return new PipelineConfig(
                e.getMaxSpeedKmh(),
                e.getH3DedupResolution()
        );
    }

    /**
     * Default values
     */
    public static void applyDefaults(PipelineConfigEntity e) {
        e.setName("Predvolená konfigurácia");
        e.setActive(true);
        e.setUserId(null);
        e.setMaxSpeedKmh(200);
        e.setH3DedupResolution(13);
    }
}
