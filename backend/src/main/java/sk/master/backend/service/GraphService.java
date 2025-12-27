package sk.master.backend.service;

import io.jenetics.jpx.GPX;
import sk.master.backend.persistence.dto.AddPointsRequest;
import sk.master.backend.persistence.entity.SavedGraph;
import sk.master.backend.persistence.model.MyGraph;

import java.util.List;

public interface GraphService {
    MyGraph generateGraph(GPX gpx);
    SavedGraph saveGraph(MyGraph graph, String name);
    MyGraph processPoints(List<AddPointsRequest.MyPoint> myPoints);

    MyGraph importGraphFromDatabase(Long graphId);
}
