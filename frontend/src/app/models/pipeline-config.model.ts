export interface PipelineConfig {
  id: number | null;
  name: string;

  // Preprocessing
  maxSpeedKmh: number;

  // H3
  h3DedupResolution: number;
}
