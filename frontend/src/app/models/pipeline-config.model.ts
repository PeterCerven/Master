export interface PipelineConfig {
  id: number | null;
  name: string;

  // Preprocessing
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
  maxSpeedKmh: number;

  // H3
  h3DedupResolution: number;
}
