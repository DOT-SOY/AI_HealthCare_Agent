package com.backend.dto.shop.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemResponse {
    private Long productId;
    private String productName;
    private String productDescription;
    private String primaryImageUrl; // TODO: product.images 중 primaryImage=true인 것의 URL
    private Instant createdAt;
}
