package sk.master.backend.persistence.dto;

import sk.master.backend.persistence.model.PlacementResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record PlacementResponseDto(
        List<StationNodeDto> stations,
        double objectiveValue,
        int totalNodes,
        Map<String, Double> coverageDistances
) {
    public record StationNodeDto(
            String id,
            double lat,
            double lon,
            int rank
    ) {}

    public static PlacementResponseDto fromResult(PlacementResult result) {
        List<StationNodeDto> stations = new ArrayList<>();
        for (int i = 0; i < result.getSelectedNodes().size(); i++) {
            var node = result.getSelectedNodes().get(i);
            stations.add(new StationNodeDto(node.getId(), node.getLat(), node.getLon(), i + 1));
        }
        return new PlacementResponseDto(
                stations,
                result.getObjectiveValue(),
                result.getNodeDistances().size(),
                result.getNodeDistances()
        );
    }
}
