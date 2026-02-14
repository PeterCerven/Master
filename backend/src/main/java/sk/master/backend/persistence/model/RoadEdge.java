package sk.master.backend.persistence.model;

public record RoadEdge(String sourceId, String targetId, double distanceMeters) {
}