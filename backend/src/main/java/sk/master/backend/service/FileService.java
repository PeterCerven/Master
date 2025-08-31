package sk.master.backend.service;

import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.dto.TrajectoryDataDto;

import java.util.List;

public interface FileService {
    void saveTrajectoryData(List<TrajectoryDataDto> data);
    List<TrajectoryDataDto> getAllTrajectoryData();

    List<TrajectoryDataDto> parseFile(MultipartFile file) throws Exception;

}
