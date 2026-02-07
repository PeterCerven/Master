package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.model.RoadGraph;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveGraphRequest {
    private String name;
    private RoadGraph graph;
}