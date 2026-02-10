export interface PipelineConfig {
  id: number | null;
  name: string;

  // Preprocessing
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
  nearDuplicateThresholdM: number;
  outlierMinNeighbors: number;
  outlierRadiusM: number;
  maxSpeedKmh: number;
  tripGapMinutes: number;

  // H3
  h3DedupResolution: number;
  h3ClusterResolution: number;

  // DBSCAN + Graph
  dbscanEpsMeters: number;
  dbscanMinPts: number;
  maxEdgeLengthM: number;
  mergeThresholdM: number;
  knnK: number;

  // Matching
  maxSnapDistanceM: number;
}
