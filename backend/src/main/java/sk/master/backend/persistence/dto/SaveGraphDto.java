package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.dto.PlacementResponseDto.StationNodeDto;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveGraphDto {
    private String name;
    private GraphDto graph;
    private List<StationNodeDto> stations;
}