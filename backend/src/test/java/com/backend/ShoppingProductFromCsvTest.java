package com.backend;

import com.backend.domain.member.Member;
import com.backend.domain.shop.ProductStatus;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductVariantRequest;
import com.backend.repository.member.MemberRepository;
import com.backend.service.shop.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * shopping_products_rewritten.csv 데이터로 상품을 생성하는 테스트.
 * 상품 이미지는 넣지 않음.
 *
 * CSV 경로: uploads/shopping_products_rewritten.csv (backend 모듈 기준)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CSV 기반 쇼핑 상품 생성")
class ShoppingProductFromCsvTest {

    private static final Pattern RECORD_END = Pattern.compile(
            ",(보충제|헬스용품|의류|기타|음식),(다이어트|벌크업|유지)\\s*$"
    );

    private static final Map<String, String> CATEGORY_TYPE_MAP = Map.of(
            "보충제", "SUPPLEMENT",
            "헬스용품", "HEALTH_GOODS",
            "의류", "CLOTHING",
            "기타", "ETC",
            "음식", "FOOD"
    );

    private static final Pattern OPTION_PRICE = Pattern.compile("\\(\\+([0-9,]+)원\\)");

    @Autowired
    private ProductService productService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("CSV 데이터로 상품 생성 (이미지 없음)")
    void createProductsFromCsv() throws IOException {
        Path csvPath = resolveCsvPath();
        if (!Files.exists(csvPath)) {
            throw new IllegalStateException("CSV 파일을 찾을 수 없습니다: " + csvPath.toAbsolutePath());
        }

        String content = Files.readString(csvPath, StandardCharsets.UTF_8);
        List<CsvRecord> records = parseCsvRecords(content);

        Member creator = memberRepository.findAll(PageRequest.of(0, 1, Sort.by("id")))
                .getContent()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("상품 생성에 사용할 회원이 없습니다."));

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int created = 0;
        for (CsvRecord record : records) {
            if (record.name == null || record.name.isBlank()) continue;

            ProductCreateRequest request = toProductCreateRequest(record);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                productService.create(request, creator.getId());
                transactionManager.commit(status);
                created++;
            } catch (Exception e) {
                transactionManager.rollback(status);
                System.err.println("상품 생성 실패: " + record.name + " - " + e.getMessage());
            }
        }

        System.out.println("=== CSV 기반 상품 생성 완료: " + created + "건 (이미지 없음) ===");
    }

    private Path resolveCsvPath() {
        Path path = Paths.get("uploads/shopping_products_rewritten.csv");
        if (Files.exists(path)) return path;
        path = Paths.get(System.getProperty("user.dir")).resolve("uploads/shopping_products_rewritten.csv");
        if (Files.exists(path)) return path;
        return Paths.get("uploads/shopping_products_rewritten.csv");
    }

    private List<CsvRecord> parseCsvRecords(String content) {
        String[] lines = content.split("\\r?\\n");
        if (lines.length < 2) return List.of();

        List<CsvRecord> records = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean skipHeader = true;

        for (String line : lines) {
            if (skipHeader) {
                skipHeader = false;
                continue;
            }
            buffer.append(line).append("\n");
            Matcher m = RECORD_END.matcher(line);
            if (m.find()) {
                String block = buffer.toString().trim();
                buffer.setLength(0);
                CsvRecord record = parseOneRecord(block, m.group(1), m.group(2));
                if (record != null) records.add(record);
            }
        }
        return records;
    }

    private CsvRecord parseOneRecord(String block, String categoryKr, String goalKr) {
        // block 끝: ...",카테고리,추천목표
        String tail = "," + categoryKr + "," + goalKr;
        int tailStart = block.lastIndexOf(tail);
        if (tailStart < 0) return null;

        String mainPart = block.substring(0, tailStart).trim();
        // mainPart = name, "options", price, "description"
        int idx = 0;
        int len = mainPart.length();

        // 1) name (첫 번째 쉼표까지, 따옴표 없음)
        int c1 = mainPart.indexOf(',', idx);
        if (c1 < 0) return null;
        String name = mainPart.substring(idx, c1).trim();
        idx = c1 + 1;

        // 2) options (따옴표로 감싸진 필드)
        if (idx >= len || mainPart.charAt(idx) != '"') return null;
        idx++;
        int optEnd = findClosingQuote(mainPart, idx);
        if (optEnd < 0) return null;
        String options = mainPart.substring(idx, optEnd).replace("\"\"", "\"");
        idx = optEnd + 1;
        if (idx < len && mainPart.charAt(idx) == ',') idx++;

        // 3) price (숫자)
        int c2 = mainPart.indexOf(',', idx);
        if (c2 < 0) return null;
        String priceStr = mainPart.substring(idx, c2).trim().replace(",", "");
        BigDecimal basePrice;
        try {
            basePrice = new BigDecimal(priceStr);
        } catch (NumberFormatException e) {
            return null;
        }
        idx = c2 + 1;

        // 4) description (따옴표로 감싸진 필드, 멀티라인)
        if (idx >= len || mainPart.charAt(idx) != '"') return null;
        idx++;
        int descEnd = findClosingQuote(mainPart, idx);
        String description = descEnd >= idx
                ? mainPart.substring(idx, descEnd).replace("\"\"", "\"")
                : "";

        CsvRecord record = new CsvRecord();
        record.name = name;
        record.options = options;
        record.basePrice = basePrice;
        record.description = description;
        record.categoryKr = categoryKr;
        return record;
    }

    private int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '"') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    i++;
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    private ProductCreateRequest toProductCreateRequest(CsvRecord record) {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName(record.name);
        request.setDescription(record.description != null ? record.description : "");
        request.setBasePrice(record.basePrice);
        request.setStatus(ProductStatus.ACTIVE);
        request.setImageFilePaths(null);
        request.setCategoryTypes(List.of(CATEGORY_TYPE_MAP.getOrDefault(record.categoryKr, "ETC")));
        request.setCategoryIds(null);

        List<ProductVariantRequest> variants = parseVariants(record.options, record.basePrice);
        request.setVariants(variants);
        return request;
    }

    private List<ProductVariantRequest> parseVariants(String optionsStr, BigDecimal basePrice) {
        if (optionsStr == null || optionsStr.isBlank()) {
            ProductVariantRequest one = new ProductVariantRequest();
            one.setOptionText("기본");
            one.setPrice(basePrice);
            one.setStockQty(100);
            one.setActive(true);
            return List.of(one);
        }

        String[] parts = optionsStr.split("\\s*/\\s*");
        List<ProductVariantRequest> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            Matcher m = OPTION_PRICE.matcher(trimmed);
            BigDecimal addPrice = BigDecimal.ZERO;
            if (m.find()) {
                String num = m.group(1).replace(",", "");
                try {
                    addPrice = new BigDecimal(num);
                } catch (NumberFormatException ignored) {
                }
            }

            ProductVariantRequest req = new ProductVariantRequest();
            req.setOptionText(trimmed);
            req.setPrice(basePrice.add(addPrice));
            req.setStockQty(100);
            req.setActive(true);
            list.add(req);
        }

        if (list.isEmpty()) {
            ProductVariantRequest one = new ProductVariantRequest();
            one.setOptionText(optionsStr);
            one.setPrice(basePrice);
            one.setStockQty(100);
            one.setActive(true);
            return List.of(one);
        }
        return list;
    }

    private static class CsvRecord {
        String name;
        String options;
        BigDecimal basePrice;
        String description;
        String categoryKr;
    }
}
