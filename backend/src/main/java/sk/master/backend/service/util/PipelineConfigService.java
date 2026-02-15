package sk.master.backend.service.util;

import sk.master.backend.persistence.model.PipelineConfig;
import sk.master.backend.persistence.dto.PipelineConfigDto;

public interface PipelineConfigService {

    PipelineConfigDto getActiveConfig();

    PipelineConfigDto updateConfig(PipelineConfigDto dto);

    PipelineConfigDto resetToDefaults();

    PipelineConfig getActivePipelineConfig();
}
