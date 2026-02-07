package sk.master.backend.service;

import sk.master.backend.persistence.dto.AddPointsRequest;
import sk.master.backend.persistence.entity.GraphEntity;
import sk.master.backend.persistence.model.Position;
import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

public interface GraphService {
    GraphEntity saveGraph(RoadGraph graph, String name);

    RoadGraph processPoints(RoadGraph graph, List<Position> positions);

    RoadGraph importGraphFromDatabase(Long graphId);
}
