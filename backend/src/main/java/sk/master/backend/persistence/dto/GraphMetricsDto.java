package sk.master.backend.persistence.dto;

public record GraphMetricsDto(
        int nodeCount,
        int edgeCount,
        double avgDegree,
        double diameterMeters,
        double clusteringCoefficient,
        double avgEdgeLengthMeters,
        double nodeDensityPerKm2,
        boolean connected,
        double radiusMeters,
        double avgBetweennessCentrality,
        int treewidth
) {}
