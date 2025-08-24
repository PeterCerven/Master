package sk.master.backend.persistence.dto;

import jakarta.validation.constraints.NotBlank;


public record TrajectoryDataDto(
        @NotBlank(message = "Latitude must not be blank")
        Double latitude,
        @NotBlank(message = "Longitude must not be blank")
        Double longitude,
        @NotBlank(message = "Timestamp must not be blank")
        Long timestamp
) {
}
