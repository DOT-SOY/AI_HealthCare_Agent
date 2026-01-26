package com.backend.controller.file;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.dto.file.FileUploadResponse;
import com.backend.service.file.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 파일 조회 컨트롤러
 * 
 * <p>이미지 등 파일을 조회하는 API를 제공합니다.
 * Security 설정에서 /api/files/view/** 경로는 permitAll()로 설정되어 있습니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );
    private static final Set<String> ALLOWED_DIRECTORIES = Set.of("products", "avatars");

    private final FileStorageService fileStorageService;

    /**
     * 파일 업로드
     * 
     * <p>이미지 파일을 업로드하고 스토리지 키(filePath)를 반환합니다.
     * 
     * <p>TODO: 추후 ADMIN 권한으로 제한 (@PreAuthorize("hasRole('ADMIN')"))
     * 
     * @param file 업로드할 파일
     * @param directory 저장할 디렉토리 (기본값: "products")
     * @return 업로드된 파일 정보
     */
    @PostMapping("/upload")
    // TODO: 추후 ADMIN 권한으로 제한
    // @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "directory", defaultValue = "products") String directory) {
        
        // 파일 필수 검증
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        // 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE) {
            long maxSizeMB = MAX_FILE_SIZE / (1024 * 1024);
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, maxSizeMB);
        }

        // 이미지 타입 검증
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            String allowedTypes = String.join(", ", ALLOWED_IMAGE_TYPES);
            throw new BusinessException(ErrorCode.FILE_INVALID_TYPE, allowedTypes);
        }

        // 디렉토리 검증
        if (!isValidDirectory(directory)) {
            throw new BusinessException(ErrorCode.FILE_INVALID_DIRECTORY);
        }

        // 파일 업로드 (서비스 레이어에서 예외 처리)
        String filePath = fileStorageService.upload(file, directory);
        String url = fileStorageService.getFileUrl(filePath);

        FileUploadResponse response = FileUploadResponse.builder()
                .filePath(filePath)
                .url(url)
                .fileSize(file.getSize())
                .contentType(contentType)
                .build();

        log.info("File uploaded successfully: filePath={}, size={} bytes", filePath, file.getSize());
        return ResponseEntity.ok(response);
    }

    /**
     * 디렉토리가 유효한지 검증합니다.
     * 화이트리스트에 있는 디렉토리만 허용하고, 경로 탐색 공격을 방지합니다.
     * 
     * @param directory 검증할 디렉토리
     * @return 유효한 디렉토리면 true, 아니면 false
     */
    private boolean isValidDirectory(String directory) {
        if (directory == null || directory.isEmpty()) {
            return false;
        }

        // 경로 탐색 공격 차단
        if (directory.contains("..") || directory.contains("/") || directory.contains("\\")) {
            return false;
        }

        // 화이트리스트 체크
        return ALLOWED_DIRECTORIES.contains(directory);
    }

    /**
     * 파일 조회
     * 
     * <p>GET /api/files/view/** 패턴으로 요청을 받아 파일을 조회합니다.
     * 경로 탐색 공격(.., 절대경로, 역슬래시)을 방지합니다.
     * 
     * @param request HTTP 요청 (경로 추출용)
     * @return 파일 리소스
     */
    @GetMapping("/view/**")
    public ResponseEntity<Resource> viewFile(HttpServletRequest request) {
        // URL 경로에서 filePath 추출
        // /api/files/view/products/uuid.jpg -> products/uuid.jpg
        String requestURI = request.getRequestURI();
        String prefix = "/api/files/view/";
        
        if (!requestURI.startsWith(prefix)) {
            log.warn("Invalid request path: {}", requestURI);
            return ResponseEntity.notFound().build();
        }
        
        String filePath = requestURI.substring(prefix.length());
        
        if (filePath == null || filePath.isEmpty()) {
            log.warn("Empty file path in request: {}", requestURI);
            return ResponseEntity.notFound().build();
        }

        // 경로 탐색 공격 방지
        if (!isValidFilePath(filePath)) {
            log.warn("Invalid file path detected (path traversal attempt): {}", filePath);
            return ResponseEntity.badRequest().build();
        }

        try {
            Resource resource = fileStorageService.download(filePath);
            
            // Content-Type 결정
            String contentType = determineContentType(filePath);
            
            // 파일명 추출 (경로의 마지막 부분)
            String filename = extractFilename(filePath);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "inline; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"")
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                    .body(resource);
        } catch (Exception e) {
            log.error("File not found: {}", filePath, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 파일 경로가 유효한지 검증합니다.
     * 경로 탐색 공격(.., 절대경로, 역슬래시)을 차단합니다.
     * 
     * @param filePath 검증할 파일 경로
     * @return 유효한 경로면 true, 아니면 false
     */
    private boolean isValidFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        
        // 경로 탐색 공격 차단: .. (상위 디렉토리)
        if (filePath.contains("..")) {
            return false;
        }
        
        // 절대 경로 차단: Windows (C:\, D:\ 등) 또는 Unix (/로 시작)
        if (filePath.startsWith("/") || filePath.matches("^[A-Za-z]:\\\\")) {
            return false;
        }
        
        // 역슬래시 차단 (Windows 경로 탐색 방지)
        if (filePath.contains("\\")) {
            return false;
        }
        
        // 정규화된 경로가 원본과 같은지 확인 (추가 보안)
        try {
            String normalized = Paths.get(filePath).normalize().toString();
            // 정규화 후에도 ..가 포함되어 있으면 차단
            if (normalized.contains("..")) {
                return false;
            }
            // 정규화 후 절대 경로가 되면 차단
            if (Paths.get(normalized).isAbsolute()) {
                return false;
            }
        } catch (Exception e) {
            log.warn("Path normalization failed: {}", filePath, e);
            return false;
        }
        
        return true;
    }

    /**
     * 파일 경로에서 파일명을 추출합니다.
     * 
     * @param filePath 파일 경로
     * @return 파일명
     */
    private String extractFilename(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "file";
        }
        
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        
        return filePath;
    }

    /**
     * 파일의 Content-Type을 결정합니다.
     */
    private String determineContentType(String filePath) {
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerPath.endsWith(".png")) {
            return "image/png";
        } else if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerPath.endsWith(".webp")) {
            return "image/webp";
        } else {
            return "application/octet-stream";
        }
    }
}

