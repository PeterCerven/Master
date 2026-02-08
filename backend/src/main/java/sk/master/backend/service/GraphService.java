package sk.master.backend.service;

import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

public interface GraphService {
    GraphEntity saveGraphToDatabase(RoadGraph graph, String name);

    RoadGraph generateRoadNetwork(RoadGraph graph, List<PositionalData> positionalData);

    RoadGraph importGraphFromDatabase(Long graphId);
}
