package sk.master.backend.service.util;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Metadata;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.model.PositionalData;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class FileServiceImpl implements FileService {

    private static final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    @Override
    public List<PositionalData> parseFile(MultipartFile file) throws Exception {
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        return switch (fileExtension) {
            case "gpx" -> parseGpxStream(file.getInputStream());
            case "geojson", "json" -> parseGeoJsonStream(file.getInputStream());
            case "csv" -> parseCsvStream(file.getInputStream());
            case null, default -> throw new IllegalArgumentException("Unsupported file format: " + fileExtension);
        };
    }

    @Override
    public List<String> listSampleFiles() throws IOException {
        Resource[] resources = resourceResolver.getResources("classpath:samples/*.*");
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<PositionalData> parseSampleFile(String filename) throws Exception {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        Resource resource = new ClassPathResource("samples/" + filename);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Sample file not found: " + filename);
        }
        String extension = FilenameUtils.getExtension(filename);
        try (InputStream inputStream = resource.getInputStream()) {
            return switch (extension) {
                case "gpx" -> parseGpxStream(inputStream);
                case "geojson", "json" -> parseGeoJsonStream(inputStream);
                case "csv" -> parseCsvStream(inputStream);
                default -> throw new IllegalArgumentException("Unsupported file format: " + extension);
            };
        }
    }

    private List<PositionalData> parseGpxStream(InputStream inputStream) throws Exception {
        try {
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
                            currentTripId
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

    private List<PositionalData> parseCsvStream(InputStream inputStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new Exception("CSV file is empty");
            headerLine = headerLine.replace("\uFEFF", "");

            String[] headers = headerLine.split(",");
            int latCol = -1, lonCol = -1, tsCol = -1, tripCol = -1;
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim().toLowerCase();
                switch (h) {
                    case "lat", "latitude"               -> latCol  = i;
                    case "lon", "longitude", "lng"       -> lonCol  = i;
                    case "timestamp", "time", "datetime" -> tsCol   = i;
                    case "trip_id", "trip", "tripid"     -> tripCol = i;
                }
            }
            if (latCol == -1 || lonCol == -1)
                throw new IllegalArgumentException("CSV file missing required lat/lon columns");

            List<PositionalData> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tokens = line.split(",");
                if (tokens.length <= Math.max(latCol, lonCol)) continue;

                double lat = Double.parseDouble(tokens[latCol].trim());
                double lon = Double.parseDouble(tokens[lonCol].trim());

                Instant timeStamp = null;
                if (tsCol != -1 && tsCol < tokens.length && !tokens[tsCol].trim().isEmpty()) {
                    try { timeStamp = Instant.parse(tokens[tsCol].trim()); } catch (Exception ignored) {}
                }

                int tripId = 1;
                if (tripCol != -1 && tripCol < tokens.length && !tokens[tripCol].trim().isEmpty()) {
                    try { tripId = Integer.parseInt(tokens[tripCol].trim()); } catch (Exception ignored) {}
                }

                result.add(new PositionalData(lat, lon, timeStamp, tripId));
            }

            if (result.isEmpty()) throw new Exception("No GPS points found in CSV file");
            return result;
        } catch (Exception e) {
            throw new Exception("Failed to parse CSV file", e);
        }
    }

    private List<PositionalData> parseGeoJsonStream(InputStream inputStream) {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(inputStream);

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

            result.add(new PositionalData(lat, lon, timestamp, tripId));
        }

        return result;
    }
}