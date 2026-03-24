package sk.master.backend.persistence.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.model.PlacementAlgorithm;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfigDto {

    private Long id;
    private String name;

    @Positive
    private double maxSpeedKmh;

    @Min(0) @Max(15)
    private int h3DedupResolution;

    @Positive
    private int kDominatingSet;

    @Positive
    private double maxRadiusMeters;

    @Min(1)
    private int iterations;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private double graspAlpha;

    @Min(1)
    private int graspEvalBudget;

    @NotNull
    private PlacementAlgorithm lastAlgorithm;

}
