package com.backend.service.shop;

import com.backend.domain.order.Order;
import com.backend.domain.order.OrderItem;
import com.backend.domain.shop.ProductCategory;
import com.backend.repository.shop.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSalesRankingService {

    private static final String KEY_PREFIX = "product:sales:category:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductCategoryRepository productCategoryRepository;

    public List<Long> getTopProductIdsByCategory(Long categoryId, int limit) {
        if (categoryId == null || limit <= 0) {
            return null;
        }
        String key = KEY_PREFIX + categoryId;
        try {
            Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
            if (members == null || members.isEmpty()) {
                return null;
            }
            List<Long> ids = new ArrayList<>(members.size());
            for (String m : members) {
                try {
                    ids.add(Long.parseLong(m));
                } catch (NumberFormatException e) {
                    log.warn("Invalid ZSet member for key={}, member={}", key, m);
                }
            }
            return ids.isEmpty() ? null : ids;
        } catch (Exception e) {
            log.warn("Redis ZREVRANGE failed: key={}, limit={}", key, limit, e);
            return null;
        }
    }

    public void recordOrderCompleted(Order order) {
        if (order == null || order.getItems() == null) {
            return;
        }
        for (OrderItem oi : order.getItems()) {
            long productId = oi.getProduct().getId();
            int qty = oi.getQty() != null ? oi.getQty() : 1;
            List<ProductCategory> productCategories = productCategoryRepository.findById_ProductId(productId);
            for (ProductCategory pc : productCategories) {
                Long categoryId = pc.getCategory().getId();
                if (categoryId == null) {
                    continue;
                }
                try {
                    String key = KEY_PREFIX + categoryId;
                    String member = String.valueOf(productId);
                    Double newScore = stringRedisTemplate.opsForZSet().incrementScore(key, member, qty);
                    if (log.isDebugEnabled()) {
                        log.debug("ZINCRBY product:sales:category:{} member={} qty={} newScore={}",
                                categoryId, member, qty, newScore);
                    }
                } catch (Exception e) {
                    log.warn("Redis ZINCRBY failed: categoryId={}, productId={}, qty={}",
                            categoryId, productId, qty, e);
                }
            }
        }
    }
}
