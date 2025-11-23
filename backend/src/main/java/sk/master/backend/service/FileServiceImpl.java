package sk.master.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import io.jenetics.jpx.GPX;
import org.apache.commons.io.FilenameUtils;

import java.io.InputStream;

@Service
public class FileServiceImpl implements FileService {


    @Override
    public GPX parseFile(MultipartFile file) throws Exception {
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        return switch (fileExtension) {
            case "gpx" -> parseGpxFile(file);
            case null, default -> throw new IllegalArgumentException("Unsupported file format: " + fileExtension);
        };
    }

    private GPX parseGpxFile(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            return GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(inputStream);
        } catch (Exception e) {
            throw new Exception("Failed to parse GPX file", e);
        }
    }
}
