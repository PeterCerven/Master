package sk.master.backend.persistence.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfig {

    private double minLat;
    private double maxLat;
    private double minLon;
    private double maxLon;
    private double maxSpeedKmh;
    private int h3DedupResolution;

}
