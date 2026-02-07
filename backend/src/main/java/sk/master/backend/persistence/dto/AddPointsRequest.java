package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.model.Position;
import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddPointsRequest {
    private List<Position> positions;
    private RoadGraph graph;
}