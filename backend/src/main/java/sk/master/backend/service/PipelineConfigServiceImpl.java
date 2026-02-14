package sk.master.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.master.backend.config.PipelineConfig;
import sk.master.backend.persistence.dto.PipelineConfigDto;
import sk.master.backend.persistence.entity.PipelineConfigEntity;
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
                e.getMinLat(), e.getMaxLat(), e.getMinLon(), e.getMaxLon(),
                e.getNearDuplicateThresholdM(), e.getOutlierMinNeighbors(),
                e.getOutlierRadiusM(), e.getMaxSpeedKmh(), e.getTripGapMinutes(),
                e.getH3DedupResolution(), e.getH3ClusterResolution(),
                e.isH3AdaptiveEnabled(), e.getH3DedupResolutionUrban(), e.getH3AdaptiveDensityThreshold(),
                e.getDbscanEpsMeters(), e.getDbscanMinPts(),
                e.getMaxEdgeLengthM(), e.getMergeThresholdM(), e.getKnnK(),
                e.getMaxBearingDiffDeg(),
                e.getMaxSnapDistanceM(),
                e.isRemoveOffRoadNodes()
        );
    }

    private void updateEntityFromDto(PipelineConfigEntity e, PipelineConfigDto d) {
        if (d.getName() != null) e.setName(d.getName());
        e.setMinLat(d.getMinLat());
        e.setMaxLat(d.getMaxLat());
        e.setMinLon(d.getMinLon());
        e.setMaxLon(d.getMaxLon());
        e.setNearDuplicateThresholdM(d.getNearDuplicateThresholdM());
        e.setOutlierMinNeighbors(d.getOutlierMinNeighbors());
        e.setOutlierRadiusM(d.getOutlierRadiusM());
        e.setMaxSpeedKmh(d.getMaxSpeedKmh());
        e.setTripGapMinutes(d.getTripGapMinutes());
        e.setH3DedupResolution(d.getH3DedupResolution());
        e.setH3ClusterResolution(d.getH3ClusterResolution());
        e.setH3AdaptiveEnabled(d.isH3AdaptiveEnabled());
        e.setH3DedupResolutionUrban(d.getH3DedupResolutionUrban());
        e.setH3AdaptiveDensityThreshold(d.getH3AdaptiveDensityThreshold());
        e.setDbscanEpsMeters(d.getDbscanEpsMeters());
        e.setDbscanMinPts(d.getDbscanMinPts());
        e.setMaxEdgeLengthM(d.getMaxEdgeLengthM());
        e.setMergeThresholdM(d.getMergeThresholdM());
        e.setKnnK(d.getKnnK());
        e.setMaxBearingDiffDeg(d.getMaxBearingDiffDeg());
        e.setMaxSnapDistanceM(d.getMaxSnapDistanceM());
        e.setRemoveOffRoadNodes(d.isRemoveOffRoadNodes());
    }

    private PipelineConfig toPipelineConfig(PipelineConfigEntity e) {
        return new PipelineConfig(
                e.getMinLat(), e.getMaxLat(), e.getMinLon(), e.getMaxLon(),
                e.getNearDuplicateThresholdM(), e.getOutlierMinNeighbors(),
                e.getOutlierRadiusM(), e.getMaxSpeedKmh(), e.getTripGapMinutes(),
                e.getH3DedupResolution(), e.getH3ClusterResolution(),
                e.isH3AdaptiveEnabled(), e.getH3DedupResolutionUrban(), e.getH3AdaptiveDensityThreshold(),
                e.getDbscanEpsMeters(), e.getDbscanMinPts(),
                e.getMaxEdgeLengthM(), e.getMergeThresholdM(), e.getKnnK(),
                e.getMaxBearingDiffDeg(),
                e.getMaxSnapDistanceM(),
                e.isRemoveOffRoadNodes()
        );
    }

    /** Default values corresponding to the original application.yml */
    public static void applyDefaults(PipelineConfigEntity e) {
        e.setName("Predvolená konfigurácia");
        e.setActive(true);
        e.setUserId(null);
        // Predspracovanie
        e.setMinLat(47.5);
        e.setMaxLat(49.7);
        e.setMinLon(16.8);
        e.setMaxLon(22.6);
        e.setNearDuplicateThresholdM(5);
        e.setOutlierMinNeighbors(1);
        e.setOutlierRadiusM(200);
        e.setMaxSpeedKmh(200);
        e.setTripGapMinutes(30);
        // H3
        e.setH3DedupResolution(12);
        e.setH3ClusterResolution(9);
        e.setH3AdaptiveEnabled(false);
        e.setH3DedupResolutionUrban(13);
        e.setH3AdaptiveDensityThreshold(5);
        // DBSCAN + Graf
        e.setDbscanEpsMeters(35);
        e.setDbscanMinPts(2);
        e.setMaxEdgeLengthM(200);
        e.setMergeThresholdM(10);
        e.setKnnK(2);
        e.setMaxBearingDiffDeg(90);
        // Map Matching
        e.setMaxSnapDistanceM(100);
        e.setRemoveOffRoadNodes(false);
    }
}
