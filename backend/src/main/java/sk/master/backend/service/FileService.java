package sk.master.backend.service;

import sk.master.backend.persistence.dto.TrajectoryDataDto;

import java.util.List;

public interface FileService {
    void importTrajectoryData(List<TrajectoryDataDto> data);
}
