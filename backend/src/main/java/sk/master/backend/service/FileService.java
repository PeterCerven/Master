package sk.master.backend.service;

import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.dto.TrajectoryDataDto;

import java.util.List;

public interface FileService {
    void importTrajectoryData(List<TrajectoryDataDto> data);

    List<TrajectoryDataDto> parseGpxFile(MultipartFile file) throws Exception;
}
