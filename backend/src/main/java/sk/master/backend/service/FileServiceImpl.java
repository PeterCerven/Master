package sk.master.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.dto.TrajectoryDataDto;
import sk.master.backend.persistence.model.TrajectoryData;
import sk.master.backend.persistence.repository.TrajectoryDataRepository;

import io.jenetics.jpx.GPX;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileServiceImpl implements FileService {

    private final TrajectoryDataRepository trajectoryDataRepository;

    public FileServiceImpl(TrajectoryDataRepository trajectoryDataRepository) {
        this.trajectoryDataRepository = trajectoryDataRepository;
    }



    @Override
    public void saveTrajectoryData(List<TrajectoryDataDto> data) {
        data.forEach(td -> trajectoryDataRepository.findByLatitudeAndLongitudeAndTimestamp(td.latitude(), td.longitude(), td.timestamp())
                .orElseGet(() -> {
                    var entity = new TrajectoryData();
                    entity.setLatitude(td.latitude());
                    entity.setLongitude(td.longitude());
                    entity.setTimestamp(td.timestamp());
                    return trajectoryDataRepository.save(entity);
                }));
    }

    @Override
    public List<TrajectoryDataDto> getAllTrajectoryData() {
        return trajectoryDataRepository.findAll().stream()
                .map(entity -> new TrajectoryDataDto(
                        entity.getLatitude(),
                        entity.getLongitude(),
                        entity.getTimestamp()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<TrajectoryDataDto> parseGpxFile(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            GPX gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(inputStream);

            return gpx.getTracks().stream()
                    .flatMap(track -> track.getSegments().stream())
                    .flatMap(segment -> segment.getPoints().stream())
                    .map(point -> new TrajectoryDataDto(
                            point.getLatitude().doubleValue(),
                            point.getLongitude().doubleValue(),
                            point.getTime().orElse(null)
                    ))
                    .collect(Collectors.toList());
        }
    }
}
