package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.model.MyGraph;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveGraphRequest {
    private String name;
    private MyGraph graph;
}