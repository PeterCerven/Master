package sk.master.backend.service;

import io.jenetics.jpx.GPX;
import sk.master.backend.persistence.model.MyGraph;

public interface GraphService {
    MyGraph generateGraph(GPX gpx);
}
