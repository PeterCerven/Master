package sk.master.backend.persistence.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
}
