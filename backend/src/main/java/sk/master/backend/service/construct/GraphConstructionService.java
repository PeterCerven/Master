package sk.master.backend.service.construct;

import sk.master.backend.persistence.dto.GraphDto;
import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

public interface GraphConstructionService {
    GraphEntity saveGraphToDatabase(GraphDto graph, String name);

    RoadGraph generateRoadNetwork(GraphDto graph, List<PositionalData> positionalData);

    GraphDto importGraphFromDatabase(Long graphId);
}
