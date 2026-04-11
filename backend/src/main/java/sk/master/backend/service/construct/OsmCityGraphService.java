package sk.master.backend.service.construct;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.wololo.jts2geojson.GeoJSONReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sk.master.backend.persistence.model.RoadGraph;
import sk.master.backend.persistence.model.RoadNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class OsmCityGraphService {

    private static final Logger log = LoggerFactory.getLogger(OsmCityGraphService.class);
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final GraphHopper hopper;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final GeometryFactory geometryFactory;

    public OsmCityGraphService(GraphHopper hopper) {
        this.hopper = hopper;
        this.httpClient = HttpClient.newHttpClient();
        this.jsonMapper = JsonMapper.builder().build();
        this.geometryFactory = new GeometryFactory();
    }

    public RoadGraph extractCityGraph(String cityName) {
        log.info("Extracting road graph for city: {}", cityName);
        CityBoundary boundary = geocodeCityBoundary(cityName);
        log.info("City boundary for {}: polygon type={}", cityName, boundary.polygon().getGeometryType());
        RoadGraph result = extractFromGraphHopper(boundary);
        log.info("Extracted {} nodes and {} edges for city: {}",
                result.getNodeCount(), result.getEdgeCount(), cityName);

        if (result.getNodeCount() > 100_000) {
            throw new IllegalArgumentException(
                "Area too large (%d nodes). Please choose a smaller city.".formatted(result.getNodeCount()));
        }

        return result;
    }

    private record CityBoundary(BBox bbox, Geometry polygon) {}

    private CityBoundary geocodeCityBoundary(String cityName) {
        try {
            String encoded = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
            String url = NOMINATIM_URL + "?q=" + encoded + "&format=json&limit=1&polygon_geojson=1&featuretype=settlement";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "RoadGraphApp/1.0 (educational)")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = jsonMapper.readTree(response.body());

            if (root.isEmpty()) {
                throw new IllegalArgumentException("City not found: " + cityName);
            }

            JsonNode result = root.get(0);

            JsonNode bboxArray = result.get("boundingbox");
            double minLat = Double.parseDouble(bboxArray.get(0).asString());
            double maxLat = Double.parseDouble(bboxArray.get(1).asString());
            double minLon = Double.parseDouble(bboxArray.get(2).asString());
            double maxLon = Double.parseDouble(bboxArray.get(3).asString());
            BBox bbox = new BBox(minLon, maxLon, minLat, maxLat);

            String geojsonStr = jsonMapper.writeValueAsString(result.get("geojson"));
            Geometry polygon = new GeoJSONReader().read(geojsonStr);

            return new CityBoundary(bbox, polygon);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to geocode city: " + cityName, e);
        }
    }

    private RoadGraph extractFromGraphHopper(CityBoundary boundary) {
        BaseGraph baseGraph = hopper.getBaseGraph();
        NodeAccess nodeAccess = baseGraph.getNodeAccess();
        RoadGraph roadGraph = new RoadGraph();
        Map<Integer, RoadNode> towerNodeCache = new HashMap<>();

        // Pre-index polygon for fast repeated contains() — avoids re-traversing polygon vertices each call
        PreparedGeometry preparedPolygon = PreparedGeometryFactory.prepare(boundary.polygon());

        // LocationIndexTree.query() returns only edges within the bbox — O(log N + E_bbox)
        // instead of iterating all edges in SK+CZ+AU — O(E_total ≈ 10M+)
        Set<Integer> visitedEdges = new HashSet<>();
        LocationIndexTree locationIndex = (LocationIndexTree) hopper.getLocationIndex();

        locationIndex.query(boundary.bbox(), edgeId -> {
            if (!visitedEdges.add(edgeId)) return; // edge can appear in multiple index cells

            var edge = baseGraph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            int baseNodeId = edge.getBaseNode();
            int adjNodeId = edge.getAdjNode();

            boolean baseInside = isInside(nodeAccess.getLat(baseNodeId), nodeAccess.getLon(baseNodeId),
                    boundary.bbox(), preparedPolygon);
            boolean adjInside = isInside(nodeAccess.getLat(adjNodeId), nodeAccess.getLon(adjNodeId),
                    boundary.bbox(), preparedPolygon);

            if (!baseInside && !adjInside) return;

            PointList geometry = edge.fetchWayGeometry(FetchMode.ALL);
            List<RoadNode> chain = buildChain(edgeId, geometry, baseNodeId, adjNodeId,
                    nodeAccess, towerNodeCache, roadGraph);

            for (int i = 0; i < chain.size() - 1; i++) {
                RoadNode a = chain.get(i);
                RoadNode b = chain.get(i + 1);
                roadGraph.addEdge(a, b, haversineDistance(a.getLat(), a.getLon(), b.getLat(), b.getLon()));
            }
        });

        return roadGraph;
    }

    private boolean isInside(double lat, double lon, BBox bbox, PreparedGeometry preparedPolygon) {
        if (!bbox.contains(lat, lon)) return false;
        return preparedPolygon.contains(geometryFactory.createPoint(new Coordinate(lon, lat)));
    }

    private List<RoadNode> buildChain(int edgeId, PointList geometry,
                                      int baseNodeId, int adjNodeId,
                                      NodeAccess nodeAccess,
                                      Map<Integer, RoadNode> towerNodeCache,
                                      RoadGraph roadGraph) {
        List<RoadNode> chain = new ArrayList<>(geometry.size());

        for (int i = 0; i < geometry.size(); i++) {
            double lat = geometry.getLat(i);
            double lon = geometry.getLon(i);

            RoadNode node;
            if (i == 0) {
                node = towerNodeCache.computeIfAbsent(baseNodeId, id -> {
                    RoadNode n = new RoadNode(String.valueOf(id), nodeAccess.getLat(id), nodeAccess.getLon(id));
                    roadGraph.addNode(n);
                    return n;
                });
            } else if (i == geometry.size() - 1) {
                node = towerNodeCache.computeIfAbsent(adjNodeId, id -> {
                    RoadNode n = new RoadNode(String.valueOf(id), nodeAccess.getLat(id), nodeAccess.getLon(id));
                    roadGraph.addNode(n);
                    return n;
                });
            } else {
                // Pillar node — intermediate point on road, unique to this edge
                node = new RoadNode(edgeId + "_p" + i, lat, lon);
                roadGraph.addNode(node);
            }

            chain.add(node);
        }

        return chain;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
