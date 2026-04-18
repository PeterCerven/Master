package sk.master.backend.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.master.backend.persistence.dto.PipelineConfigDto;
import sk.master.backend.persistence.entity.PipelineConfigEntity;
import sk.master.backend.persistence.model.PipelineConfig;
import sk.master.backend.persistence.model.PlacementAlgorithm;
import sk.master.backend.persistence.repository.PipelineConfigRepository;
import sk.master.backend.persistence.repository.UserRepository;

import java.util.Objects;

@Service
public class PipelineConfigServiceImpl implements PipelineConfigService {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfigServiceImpl.class);

    private final PipelineConfigRepository repository;
    private final UserRepository userRepository;

    public PipelineConfigServiceImpl(PipelineConfigRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public PipelineConfigDto getActiveConfig() {
        return toDto(getOrCreateUserConfig(getCurrentUserId()));
    }

    @Override
    @Transactional
    public PipelineConfigDto updateConfig(PipelineConfigDto dto) {
        PipelineConfigEntity entity = getOrCreateUserConfig(getCurrentUserId());
        updateEntityFromDto(entity, dto);
        entity = repository.save(entity);
        log.info("Configuration updated: id={}", entity.getId());
        return toDto(entity);
    }

    @Override
    @Transactional
    public PipelineConfigDto resetToDefaults() {
        PipelineConfigEntity entity = getOrCreateUserConfig(getCurrentUserId());
        applyDefaults(entity);
        entity.setUserId(getCurrentUserId());
        entity = repository.save(entity);
        log.info("Configuration reset to defaults: id={}", entity.getId());
        return toDto(entity);
    }

    @Override
    @Transactional
    public PipelineConfig getActivePipelineConfig() {
        return toPipelineConfig(getOrCreateUserConfig(getCurrentUserId()));
    }

    // ====== Private helpers ======

    private Long getCurrentUserId() {
        String email = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email))
                .getId();
    }

    private PipelineConfigEntity getOrCreateUserConfig(Long userId) {
        return repository.findByUserIdAndActiveTrue(userId).orElseGet(() -> {
            PipelineConfigEntity defaultConfig = repository.findByUserIdIsNullAndActiveTrue()
                    .orElseThrow(() -> new IllegalStateException("No default pipeline configuration found"));

            PipelineConfigEntity userConfig = new PipelineConfigEntity();
            userConfig.setUserId(userId);
            userConfig.setName(defaultConfig.getName());
            userConfig.setActive(true);
            userConfig.setMaxSpeedKmh(defaultConfig.getMaxSpeedKmh());
            userConfig.setH3DedupResolution(defaultConfig.getH3DedupResolution());
            userConfig.setKDominatingSet(defaultConfig.getKDominatingSet());
            userConfig.setMaxRadiusMeters(defaultConfig.getMaxRadiusMeters());
            userConfig.setIterations(defaultConfig.getIterations());
            userConfig.setGraspAlpha(defaultConfig.getGraspAlpha());
            userConfig.setGraspEvalBudget(defaultConfig.getGraspEvalBudget());
            userConfig.setCityCountry(defaultConfig.getCityCountry());
            userConfig.setRetainLargestComponentPercent(defaultConfig.getRetainLargestComponentPercent());
            userConfig.setCityBoundaryBufferMeters(defaultConfig.getCityBoundaryBufferMeters());
            userConfig.setLastAlgorithm(defaultConfig.getLastAlgorithm());

            userConfig = repository.save(userConfig);
            log.info("Created new config for userId={}: id={}", userId, userConfig.getId());
            return userConfig;
        });
    }

    // ====== Mapping methods ======

    private PipelineConfigDto toDto(PipelineConfigEntity e) {
        return new PipelineConfigDto(
                e.getId(), e.getName(),
                e.getMaxSpeedKmh(),
                e.getH3DedupResolution(),
                e.getKDominatingSet(),
                e.getMaxRadiusMeters(),
                e.getIterations(),
                e.getGraspAlpha(),
                e.getGraspEvalBudget(),
                e.getCityCountry(),
                e.getRetainLargestComponentPercent(),
                e.getCityBoundaryBufferMeters(),
                e.getLastAlgorithm()
        );
    }

    private void updateEntityFromDto(PipelineConfigEntity e, PipelineConfigDto d) {
        if (d.getName() != null) e.setName(d.getName());
        e.setMaxSpeedKmh(d.getMaxSpeedKmh());
        e.setH3DedupResolution(d.getH3DedupResolution());
        e.setKDominatingSet(d.getKDominatingSet());
        e.setMaxRadiusMeters(d.getMaxRadiusMeters());
        e.setIterations(d.getIterations());
        e.setGraspAlpha(d.getGraspAlpha());
        e.setGraspEvalBudget(d.getGraspEvalBudget());
        e.setCityCountry(d.getCityCountry());
        e.setRetainLargestComponentPercent(d.getRetainLargestComponentPercent());
        e.setCityBoundaryBufferMeters(d.getCityBoundaryBufferMeters());
        if (d.getLastAlgorithm() != null) e.setLastAlgorithm(d.getLastAlgorithm());
    }

    private PipelineConfig toPipelineConfig(PipelineConfigEntity e) {
        return new PipelineConfig(
                e.getMaxSpeedKmh(),
                e.getH3DedupResolution(),
                e.getCityCountry(),
                e.getRetainLargestComponentPercent(),
                e.getCityBoundaryBufferMeters()
        );
    }

    /**
     * Default values
     */
    public static void applyDefaults(PipelineConfigEntity e) {
        e.setName("Default Config");
        e.setActive(true);
        e.setUserId(null);
        e.setMaxSpeedKmh(200);
        e.setH3DedupResolution(12);
        e.setKDominatingSet(2);
        e.setMaxRadiusMeters(1000.0);
        e.setIterations(10);
        e.setGraspAlpha(0.3);
        e.setGraspEvalBudget(500);
        e.setCityCountry(null);
        e.setRetainLargestComponentPercent(0.1);
        e.setCityBoundaryBufferMeters(100.0);
        e.setLastAlgorithm(PlacementAlgorithm.RANDOM_STRATEGY);
    }
}
