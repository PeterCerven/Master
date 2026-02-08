package sk.master.backend.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class PipelineConfig {

    // ===== Krok 1: Predspracovanie =====

    /** Minimálna platná zemepisná šírka pre bounding box filter */
    @Value("${pipeline.preprocessing.min-lat:47.5}")
    private double minLat;

    /** Maximálna platná zemepisná šírka */
    @Value("${pipeline.preprocessing.max-lat:49.7}")
    private double maxLat;

    /** Minimálna platná zemepisná dĺžka */
    @Value("${pipeline.preprocessing.min-lon:16.8}")
    private double minLon;

    /** Maximálna platná zemepisná dĺžka */
    @Value("${pipeline.preprocessing.max-lon:22.6}")
    private double maxLon;

    /** Vzdialenosť pod ktorou dva body považujeme za near-duplicate (v metroch) */
    @Value("${pipeline.preprocessing.near-duplicate-threshold-m:5}")
    private double nearDuplicateThresholdM;

    /** Minimálny počet susedov v okruhu pre density filter (outlier removal) */
    @Value("${pipeline.preprocessing.outlier-min-neighbors:2}")
    private int outlierMinNeighbors;

    /** Polomer pre density filter (v metroch) */
    @Value("${pipeline.preprocessing.outlier-radius-m:75}")
    private double outlierRadiusM;

    /** Maximálna realistická rýchlosť medzi po sebe idúcimi bodmi (km/h).
     *  Body prekračujúce túto rýchlosť sú považované za GPS šum. */
    @Value("${pipeline.preprocessing.max-speed-kmh:200}")
    private double maxSpeedKmh;

    /** Minimálna pauza medzi bodmi na rozdelenie trás (v minútach).
     *  Ak je časový rozdiel väčší, body patria do rôznych jázd. */
    @Value("${pipeline.preprocessing.trip-gap-minutes:30}")
    private long tripGapMinutes;

    // ===== Krok 2: H3 Spatial Index =====

    /** H3 resolution pre deduplikáciu (~29m edge pri res 11) */
    @Value("${pipeline.h3.dedup-resolution:11}")
    private int h3DedupResolution;

    /** H3 resolution pre spatial partitioning (~200m edge pri res 9) */
    @Value("${pipeline.h3.cluster-resolution:9}")
    private int h3ClusterResolution;

    // ===== Krok 3: DBSCAN + Graf =====

    /** DBSCAN epsilon — max vzdialenosť medzi bodmi v klastri (v metroch) */
    @Value("${pipeline.dbscan.eps-meters:25}")
    private double dbscanEpsMeters;

    /** DBSCAN minPts — min bodov pre core point */
    @Value("${pipeline.dbscan.min-pts:5}")
    private int dbscanMinPts;

    /** Max vzdialenosť hrany (v metroch) — hrany dlhšie sa nepridávajú */
    @Value("${pipeline.graph.max-edge-length-m:300}")
    private double maxEdgeLengthM;

    /** Merge threshold — body bližšie k existujúcemu nodu sa zlúčia (v metroch) */
    @Value("${pipeline.graph.merge-threshold-m:10}")
    private double mergeThresholdM;

    /** Počet najbližších susedov pri KNN spojení */
    @Value("${pipeline.graph.knn-k:5}")
    private int knnK;

    // ===== Krok 4: Map Matching =====

    /** Max snap vzdialenosť — body ďalej od cesty sa označia ako off-road */
    @Value("${pipeline.matching.max-snap-distance-m:50}")
    private double maxSnapDistanceM;


}
