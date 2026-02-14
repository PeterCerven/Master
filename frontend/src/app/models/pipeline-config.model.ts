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
  h3AdaptiveEnabled: boolean;
  h3DedupResolutionUrban: number;
  h3AdaptiveDensityThreshold: number;

  // DBSCAN + Graph
  dbscanEpsMeters: number;
  dbscanMinPts: number;
  maxEdgeLengthM: number;
  mergeThresholdM: number;
  knnK: number;
  maxBearingDiffDeg: number;

  // Matching
  maxSnapDistanceM: number;
}
