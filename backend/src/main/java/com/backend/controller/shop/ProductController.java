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
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 상품 등록
    // TODO: 추후 ADMIN 권한으로 제한
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductCreateRequest request) {
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

    // 상품 정보 수정
    // TODO: 추후 ADMIN 권한으로 제한
    @PatchMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        ProductResponse response = productService.update(id, request);
        return ResponseEntity.ok(response);
    }

    // 상품 삭제
    // TODO: 추후 ADMIN 권한으로 제한
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
