package sk.master.backend.service;

import sk.master.backend.persistence.model.PipelineConfig;
import sk.master.backend.persistence.dto.PipelineConfigDto;

public interface PipelineConfigService {

    /** Returns the active configuration as a DTO for the frontend */
    PipelineConfigDto getActiveConfig();

    /** Updates the active configuration */
    PipelineConfigDto updateConfig(PipelineConfigDto dto);

    /** Resets to default values */
    PipelineConfigDto resetToDefaults();

    /** Returns the active configuration as a runtime POJO for the pipeline */
    PipelineConfig getActivePipelineConfig();
}
