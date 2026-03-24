export interface GraphMetrics {
  nodeCount: number;
  edgeCount: number;
  avgDegree: number;
  diameterMeters: number;
  clusteringCoefficient: number;
  avgEdgeLengthMeters: number;
  nodeDensityPerKm2: number;
  connected: boolean;
  radiusMeters: number;
  avgBetweennessCentrality: number;
  treewidth: number;
}

export interface GraphResponseDto {
  nodes: GraphNodeDto[];
  edges: GraphEdgeDto[];
  metrics: GraphMetrics | null;
}

export interface GraphNodeDto {
  id: string;
  lat: number;
  lon: number;
  roadName: string | null;
  roadClass: string | null;
}

export interface GraphEdgeDto {
  sourceId: string;
  targetId: string;
  distanceMeters: number;
  roadName: string | null;
}

export interface PlacementRequestDto {
  graph: {
    nodes: { id: string; lat: number; lon: number }[];
    edges: { sourceId: string; targetId: string; distanceMeters: number }[];
  };
  algorithm: 'RANDOM_STRATEGY' | 'GRASP_STRATEGY' | 'GREEDY_STRATEGY';
  k: number;
  maxRadiusMeters: number;
  iterations: number;
  graspAlpha: number;
  graspEvalBudget: number;
}

export interface StationNodeDto {
  id: string;
  lat: number;
  lon: number;
  rank: number;
}

export interface PlacementResponseDto {
  stations: StationNodeDto[];
  objectiveValue: number;
  totalNodes: number;
  coverageDistances: Record<string, number>;
}

export interface GraphSummaryDto {
  id: number;
  name: string;
  createdAt: string;
  nodeCount: number;
  edgeCount: number;
  stationCount: number;
}

export interface SavedGraphResponseDto {
  id: number;
  name: string;
  createdAt: string;
  nodes: GraphNodeDto[];
  edges: GraphEdgeDto[];
  stations: StationNodeDto[];
  metrics: GraphMetrics | null;
}
