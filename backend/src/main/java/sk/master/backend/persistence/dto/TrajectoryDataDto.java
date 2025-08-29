package sk.master.backend.persistence.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;


public record TrajectoryDataDto(
        @NotBlank(message = "Latitude must not be blank")
        Double latitude,

        @NotBlank(message = "Longitude must not be blank")
        Double longitude,

        Instant timestamp
) {
}
