package sk.master.backend.service;

import sk.master.backend.persistence.model.SnappedPoint;

public interface MapMatchingService {
    SnappedPoint snapToRoad(double lat, double lon);
}
