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

export interface GraphPoint {
  lat: number;
  lon: number;
}

export interface PlacementRequestDto {
  graph: {
    nodes: { id: string; lat: number; lon: number }[];
    edges: { sourceId: string; targetId: string; distanceMeters: number }[];
  };
  algorithm: 'K_DOMINATING_SET' | 'K_CENTRE';
  k: number;
  maxRadiusMeters: number;
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
