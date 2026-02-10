package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddPointsRequest {
    private List<PositionalData> positionalData;
    private GraphResponseDto graph;
}