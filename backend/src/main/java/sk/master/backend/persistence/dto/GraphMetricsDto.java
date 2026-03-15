package sk.master.backend.persistence.dto;

public record GraphMetricsDto(
        int nodeCount,
        int edgeCount,
        double avgDegree,
        double diameterMeters,
        double clusteringCoefficient,
        double avgEdgeLengthMeters,
        double nodeDensityPerKm2,
        double avgShortestPathMeters,
        double avgBetweennessCentrality,
        boolean approximated
) {}
