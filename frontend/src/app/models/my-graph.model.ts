export interface GraphResponseDto {
  nodes: GraphNodeDto[];
  edges: GraphEdgeDto[];
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
  algorithm: 'RANDOM_STRATEGY' | 'CUSTOM_STRATEGY' | 'GREEDY_STRATEGY';
  k: number;
  maxRadiusMeters: number;
  iterations: number;
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
