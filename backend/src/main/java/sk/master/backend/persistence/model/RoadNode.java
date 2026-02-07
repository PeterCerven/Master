package sk.master.backend.persistence.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoadNode {
    private long id;
    private double lon;
    private double lat;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RoadNode node = (RoadNode) o;
        return id == node.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
