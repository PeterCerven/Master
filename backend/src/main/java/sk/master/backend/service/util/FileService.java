package sk.master.backend.service.util;

import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.model.PositionalData;

import java.util.List;

public interface FileService {
    List<PositionalData> parseFile(MultipartFile file) throws Exception;

}
