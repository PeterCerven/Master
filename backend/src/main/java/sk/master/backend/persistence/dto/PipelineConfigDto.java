package sk.master.backend.persistence.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfigDto {

    private Long id;
    private String name;

    // Predspracovanie
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private double minLat;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private double maxLat;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private double minLon;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private double maxLon;

    @Positive
    private double maxSpeedKmh;

    @Min(0) @Max(15)
    private int h3DedupResolution;

}
