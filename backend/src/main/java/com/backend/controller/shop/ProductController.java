package com.backend.controller.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductSearchRequest;
import com.backend.dto.shop.request.ProductUpdateRequest;
import com.backend.dto.shop.response.ProductResponse;
import com.backend.service.shop.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 상품 등록 (ADMIN 전용)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductCreateRequest request) {
        // #region agent log
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Paths.get("c:\\Users\\EZEN\\Downloads\\healthcare\\.cursor\\debug.log"),
                    "{\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"H1\",\"location\":\"ProductController.create\",\"message\":\"enter create\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {}
        // #endregion
        // TODO: 추후 JWT에서 사용자 ID 추출
        // @AuthenticationPrincipal Long userId
        Long createdBy = 1L; // 임시 값
        
        ProductResponse response = productService.create(request, createdBy);
        
        return ResponseEntity
                .created(URI.create("/api/products/" + response.getId()))
                .body(response);
    }

    // 상품 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        ProductResponse response = productService.findById(id);
        return ResponseEntity.ok(response);
    }

    // 상품 리스트 조회 (페이징, 검색, 필터링, 정렬)
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> findAll(
            @Valid @ModelAttribute PageRequest pageRequest,
            @Valid @ModelAttribute ProductSearchRequest searchRequest) {
        PageResponse<ProductResponse> response = productService.findAll(pageRequest, searchRequest);
        return ResponseEntity.ok(response);
    }

    // 상품 정보 수정 (ADMIN 전용)
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        // #region agent log
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Paths.get("c:\\Users\\EZEN\\Downloads\\healthcare\\.cursor\\debug.log"),
                    "{\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"H1\",\"location\":\"ProductController.update\",\"message\":\"enter update\",\"data\":{\"id\":" + id + "},\"timestamp\":" + System.currentTimeMillis() + "}\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {}
        // #endregion
        ProductResponse response = productService.update(id, request);
        return ResponseEntity.ok(response);
    }

    // 상품 삭제 (ADMIN 전용)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // #region agent log
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Paths.get("c:\\Users\\EZEN\\Downloads\\healthcare\\.cursor\\debug.log"),
                    "{\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"H1\",\"location\":\"ProductController.delete\",\"message\":\"enter delete\",\"data\":{\"id\":" + id + "},\"timestamp\":" + System.currentTimeMillis() + "}\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {}
        // #endregion
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
