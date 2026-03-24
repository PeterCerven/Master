package sk.master.backend.persistence.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlacementParams {
    private final int k;
    private final Double maxRadiusMeters;
    private final int iterations;
    private final double graspAlpha;
    private final int graspEvalBudget;
}
