package sk.master.backend.persistence.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyGraph {
    private List<Node> nodes;
    private List<Edge> edges;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private long id;
        private double lat;
        private double lon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        private long sourceId;
        private long targetId;
        private double weight;
    }
}
