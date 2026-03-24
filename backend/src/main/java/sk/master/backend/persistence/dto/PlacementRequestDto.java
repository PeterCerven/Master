package sk.master.backend.persistence.dto;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.model.PlacementAlgorithm;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlacementRequestDto {

    @NotNull
    private GraphDto graph;

    @NotNull
    private PlacementAlgorithm algorithm;

    @Positive
    private int k;

    @Positive
    private Double maxRadiusMeters;

    @Min(1)
    private int iterations = 1;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private double graspAlpha = 0.3;

    @Min(1)
    private int graspEvalBudget = 500;
}
