package com.backend.controller.ocr;

import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.service.ocr.ocrInbodyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/member-body-info/ocr")
@RequiredArgsConstructor
public class ocrInbodyController {

    private final ocrInbodyService ocrInbodyService;

    /**
     * [인바디 OCR 분석]
     * 이미지를 업로드받아 분석 후, **저장하지 않고** 추출된 데이터를 반환합니다.
     * 사용자가 프론트엔드 검증 모달에서 확인 후 최종 저장을 요청합니다.
     */
    @PostMapping("/analyze")
    public ResponseEntity<MemberInfoBodyDTO> analyzeInbody(
            @AuthenticationPrincipal String email,
            @RequestParam("file") MultipartFile file) {
        
        log.info("[OCR] 인바디 분석 요청 (저장 X): user={}, filename={}", email, file.getOriginalFilename());
        MemberInfoBodyDTO result = ocrInbodyService.extractData(email, file);
        return ResponseEntity.ok(result);
    }
}
