package sk.master.backend.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddPointsRequest {
    private List<MyPoint> points;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyPoint {
        private double lat;
        private double lon;
    }
}