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
