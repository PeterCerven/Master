package sk.master.backend.service;

import sk.master.backend.persistence.model.PositionalData;

import java.util.List;

public interface MapMatchingService {
    List<PositionalData> matchTrajectory(List<PositionalData> trip);
}
