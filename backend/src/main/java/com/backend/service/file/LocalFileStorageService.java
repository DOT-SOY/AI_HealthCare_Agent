package com.backend.service.file;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 로컬 파일 시스템 기반 파일 저장소 서비스
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path rootLocation;
    private final String baseUrl;

    public LocalFileStorageService(
            @Value("${file.storage.root-location:uploads}") String rootLocation,
            @Value("${file.storage.base-url:http://localhost:8080/api/files/view}") String baseUrl) {
        this.rootLocation = Paths.get(rootLocation).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        try {
            Files.createDirectories(this.rootLocation);
            log.info("File storage initialized at: {}", this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize file storage", e);
        }
    }

    @Override
    public String upload(MultipartFile file, String directory) {
        try {
            // 파일명 생성: UUID + 원본 확장자
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;
            
            // 디렉토리 경로 생성
            Path directoryPath = this.rootLocation.resolve(directory);
            Files.createDirectories(directoryPath);
            
            // 파일 저장
            Path targetLocation = directoryPath.resolve(filename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            
            // 스토리지 키 반환 (디렉토리/파일명)
            String filePath = directory + "/" + filename;
            log.info("File uploaded: {}", filePath);
            
            return filePath;
        } catch (IOException e) {
            log.error("File upload failed: directory={}, filename={}", directory, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        } catch (Exception e) {
            log.error("Unexpected error during file upload: directory={}, filename={}", directory, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }
    }

    @Override
    public Resource download(String filePath) {
        try {
            Path file = this.rootLocation.resolve(filePath).normalize();
            
            if (!file.startsWith(this.rootLocation)) {
                throw new RuntimeException("Cannot access file outside storage directory");
            }
            
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filePath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not load file: " + filePath, e);
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            Path file = this.rootLocation.resolve(filePath).normalize();
            
            if (!file.startsWith(this.rootLocation)) {
                throw new RuntimeException("Cannot delete file outside storage directory");
            }
            
            Files.deleteIfExists(file);
            log.info("File deleted: {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete file: " + filePath, e);
        }
    }

    @Override
    public String getFileUrl(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        // URL 인코딩은 필요시 추가
        return baseUrl + "/" + filePath;
    }
}

