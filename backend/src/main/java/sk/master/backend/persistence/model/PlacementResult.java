package sk.master.backend.persistence.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class PlacementResult {
    private final List<RoadNode> selectedNodes;
    private final double objectiveValue;
    private final Map<String, Double> nodeDistances;
}
