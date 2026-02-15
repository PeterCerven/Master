package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveGraphRequest {
    private String name;
    private GraphDto graph;
}