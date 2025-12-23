package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sk.master.backend.persistence.model.MyGraph;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePointsRequest {
    private List<MyPoint> myPoints;
    private MyGraph graph;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyPoint {
        private double lat;
        private double lon;
    }
}