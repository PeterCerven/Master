package sk.master.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sk.master.backend.persistence.dto.PipelineConfigDto;
import sk.master.backend.service.PipelineConfigService;

@RestController
@RequestMapping("/api/pipeline-config")
public class PipelineConfigController {

    private final PipelineConfigService configService;

    public PipelineConfigController(PipelineConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<PipelineConfigDto> getActiveConfig() {
        return ResponseEntity.ok(configService.getActiveConfig());
    }

    @PutMapping
    public ResponseEntity<PipelineConfigDto> updateConfig(@Valid @RequestBody PipelineConfigDto dto) {
        return ResponseEntity.ok(configService.updateConfig(dto));
    }

    @PostMapping("/reset")
    public ResponseEntity<PipelineConfigDto> resetToDefaults() {
        return ResponseEntity.ok(configService.resetToDefaults());
    }
}
