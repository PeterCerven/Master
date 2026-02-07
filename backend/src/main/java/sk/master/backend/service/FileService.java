package sk.master.backend.service;

import io.jenetics.jpx.GPX;
import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.model.Position;

import java.util.List;

public interface FileService {
    List<Position> parseFile(MultipartFile file) throws Exception;

}
