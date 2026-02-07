package sk.master.backend.persistence.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoadEdge {

    private long sourceId;
    private long targetId;
    private double weight;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RoadEdge edge = (RoadEdge) o;
        return sourceId == edge.sourceId && targetId == edge.targetId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceId, targetId);
    }
}