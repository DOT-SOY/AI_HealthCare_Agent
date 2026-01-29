package com.backend.service.file;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 서비스 인터페이스
 * 
 * <p>파일 업로드, 다운로드, 삭제 및 URL 생성 기능을 제공합니다.
 * 
 * <p>TODO (MVP 이후): 미연결 업로드 파일 청소 배치 작업
 * - 상품 이미지 업로드 후 일정 시간(예: 24시간) 내에 ProductImage에 연결되지 않은 파일 정리
 * - 주기적으로 실행되는 배치 작업으로 구현 예정
 */
public interface FileStorageService {
    
    /**
     * 파일을 업로드하고 스토리지 키(파일 경로)를 반환합니다.
     * 
     * @param file 업로드할 파일
     * @param directory 저장할 디렉토리 (예: "products", "members")
     * @return 스토리지 키 (파일 경로)
     */
    String upload(MultipartFile file, String directory);
    
    /**
     * 파일을 다운로드합니다.
     * 
     * @param filePath 스토리지 키 (파일 경로)
     * @return 파일 리소스
     */
    Resource download(String filePath);
    
    /**
     * 파일을 삭제합니다.
     * 
     * @param filePath 스토리지 키 (파일 경로)
     */
    void delete(String filePath);
    
    /**
     * 스토리지 키를 기반으로 접근 가능한 URL을 생성합니다.
     * 
     * @param filePath 스토리지 키 (파일 경로)
     * @return 접근 가능한 URL
     */
    String getFileUrl(String filePath);
}

