package sk.master.backend.persistence.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoadGraph {
    private List<RoadNode> nodes;
    private List<RoadEdge> edges;

}

