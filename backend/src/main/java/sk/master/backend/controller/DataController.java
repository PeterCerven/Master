package sk.master.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.master.backend.persistence.dto.TrajectoryDataDto;
import sk.master.backend.service.FileService;

import java.util.List;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private final FileService fileService;

    public DataController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/import")
    public ResponseEntity<String> importData(@RequestBody List<TrajectoryDataDto> data) {
        try {
            fileService.importTrajectoryData(data);
            return ResponseEntity.ok("Data imported successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error importing data: " + e.getMessage());
        }
    }
}
