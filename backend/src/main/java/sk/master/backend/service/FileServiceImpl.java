package sk.master.backend.service;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Metadata;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.model.PositionalData;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

@Service
public class FileServiceImpl implements FileService {


    @Override
    public List<PositionalData> parseFile(MultipartFile file) throws Exception {
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        return switch (fileExtension) {
            case "gpx" -> parseGpxFile(file);
            case null, default -> throw new IllegalArgumentException("Unsupported file format: " + fileExtension);
        };
    }

    private List<PositionalData> parseGpxFile(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            GPX gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(inputStream);

            Instant metadataTime = gpx.getMetadata()
                    .flatMap(Metadata::getTime)
                    .orElse(null);

            if (gpx.tracks() != null) {
                return gpx.tracks()
                        .flatMap(Track::segments)
                        .flatMap(TrackSegment::points)
                        .map(point -> new PositionalData(
                                point.getLatitude().doubleValue(),
                                point.getLongitude().doubleValue(),
                                point.getTime().orElse(metadataTime)
                                ))
                        .toList();
            }
            throw new Exception("No GPS points");
        } catch (Exception e) {
            throw new Exception("Failed to parse GPX file", e);
        }
    }
}
