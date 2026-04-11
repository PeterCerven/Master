package sk.master.backend.service.construct;

import sk.master.backend.persistence.dto.GraphDto;
import sk.master.backend.persistence.dto.GraphMetricsDto;
import sk.master.backend.persistence.dto.GraphSummaryDto;
import sk.master.backend.persistence.dto.PlacementResponseDto;
import sk.master.backend.persistence.dto.SavedGraphDto;
import sk.master.backend.persistence.model.PositionalData;
import sk.master.backend.persistence.model.RoadGraph;

import java.util.List;

public interface GraphConstructionService {
    GraphSummaryDto saveGraphToDatabase(GraphDto graph, List<PlacementResponseDto.StationNodeDto> stations, String name, Long userId);

    RoadGraph generateRoadNetwork(GraphDto graph, List<PositionalData> positionalData);

    RoadGraph importCityGraph(String city);

    GraphMetricsDto computeMetrics(RoadGraph roadGraph);

    GraphMetricsDto computeCurrentGraphMetrics();

    SavedGraphDto importGraphFromDatabase(Long graphId, Long userId);

    List<GraphSummaryDto> listUserGraphs(Long userId);

    void deleteGraph(Long graphId, Long userId);

    GraphSummaryDto renameGraph(Long graphId, String newName, Long userId);
}
