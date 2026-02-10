package com.backend.service.ocr;

import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ocrInbodyService {
    /**
     * 인바디 이미지를 분석하여 데이터만 추출합니다 (DB 저장 X).
     * 기존 데이터가 있다면 병합하여 반환합니다.
     */
    MemberInfoBodyDTO extractData(String email, MultipartFile file);
}
