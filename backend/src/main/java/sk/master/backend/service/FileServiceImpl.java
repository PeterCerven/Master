package sk.master.backend.service;

import org.springframework.stereotype.Service;
import sk.master.backend.persistence.dto.TrajectoryDataDto;
import sk.master.backend.persistence.model.TrajectoryData;
import sk.master.backend.persistence.repository.TrajectoryDataRepository;

import java.util.List;

@Service
public class FileServiceImpl implements FileService {

    private final TrajectoryDataRepository trajectoryDataRepository;

    public FileServiceImpl(TrajectoryDataRepository trajectoryDataRepository) {
        this.trajectoryDataRepository = trajectoryDataRepository;
    }

    @Override
    public void importTrajectoryData(List<TrajectoryDataDto> data) {
        data.forEach(td -> trajectoryDataRepository.findByLatitudeAndLongitudeAndTimestamp(td.latitude(), td.longitude(), td.timestamp())
                .orElseGet(() -> {
                    var entity = new TrajectoryData();
                    entity.setLatitude(td.latitude());
                    entity.setLongitude(td.longitude());
                    entity.setTimestamp(td.timestamp());
                    return trajectoryDataRepository.save(entity);
                }));
    }
}
