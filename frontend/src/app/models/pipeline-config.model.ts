export interface PipelineConfig {
  id: number | null;
  name: string;

  // Preprocessing
  maxSpeedKmh: number;

  // H3
  h3DedupResolution: number;

  // Placement
  kDominatingSet: number;
  maxRadiusMeters: number;
  iterations: number;
  graspAlpha: number;
  graspEvalBudget: number;
  lastAlgorithm: 'RANDOM_STRATEGY' | 'GREEDY_STRATEGY' | 'GRASP_STRATEGY';
}
