package sk.master.backend.service.util;

import org.springframework.web.multipart.MultipartFile;
import sk.master.backend.persistence.model.PositionalData;

import java.io.IOException;
import java.util.List;

public interface FileService {
    List<PositionalData> parseFile(MultipartFile file) throws Exception;

    List<String> listSampleFiles() throws IOException;

    List<PositionalData> parseSampleFile(String filename) throws Exception;
}
