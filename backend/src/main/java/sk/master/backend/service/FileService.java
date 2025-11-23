package sk.master.backend.service;

import io.jenetics.jpx.GPX;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    GPX parseFile(MultipartFile file) throws Exception;

}
