package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.model.PositionalData;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddPointsRequest {
    private List<PositionalData> positionalData;
    private GraphDto graph;
}