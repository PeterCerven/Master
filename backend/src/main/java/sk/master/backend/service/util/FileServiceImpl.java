package sk.master.backend.service.util;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Metadata;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.model.PositionalData;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;


import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileServiceImpl implements FileService {


    @Override
    public List<PositionalData> parseFile(MultipartFile file) throws Exception {
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        return switch (fileExtension) {
            case "gpx" -> parseGpxFile(file);
            case "geojson", "json" -> parseGeoJsonFile(file);
            case null, default -> throw new IllegalArgumentException("Unsupported file format: " + fileExtension);
        };
    }

    private List<PositionalData> parseGpxFile(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            GPX gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(inputStream);

            Instant metadataTime = gpx.getMetadata()
                    .flatMap(Metadata::getTime)
                    .orElse(null);

            List<PositionalData> result = new ArrayList<>();
            // Use an array to allow mutation inside the lambda expressions
            int[] tripCounter = {1};

            if (gpx.tracks() != null) {
                gpx.tracks().forEach(track -> track.segments().forEach(segment -> {
                    int currentTripId = tripCounter[0];

                    segment.points().forEach(point -> result.add(new PositionalData(
                            point.getLatitude().doubleValue(),
                            point.getLongitude().doubleValue(),
                            point.getTime().orElse(metadataTime),
                            currentTripId // Assign the tripId right here!
                    )));

                    // Increment trip ID for the next segment (a new continuous drive)
                    tripCounter[0]++;
                }));
                return result;
            }
            throw new Exception("No GPS points");
        } catch (Exception e) {
            throw new Exception("Failed to parse GPX file", e);
        }
    }

    private List<PositionalData> parseGeoJsonFile(MultipartFile file) throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(file.getBytes());

        List<PositionalData> result = new ArrayList<>();
        int defaultTripId = 1;

        for (JsonNode feature : root.get("features")) {
            JsonNode coords = feature.get("geometry").get("coordinates");
            JsonNode props = feature.get("properties");

            double lon = coords.get(0).asDouble();
            double lat = coords.get(1).asDouble();
            Instant timestamp = Instant.parse(props.get("timestamp").asString());

            // Check if the GeoJSON properties contain a trip identifier
            int tripId = props.has("trip_id") ? props.get("trip_id").asInt() : defaultTripId;

            // Use the constructor with tripId
            result.add(new PositionalData(lat, lon, timestamp, tripId));
        }

        return result;
    }
}
