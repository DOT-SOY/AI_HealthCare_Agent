package com.backend.repository.shop;

import com.backend.domain.shop.Wishlist;
import com.backend.domain.shop.WishlistId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, WishlistId> {
    
    // memberId와 productId로 존재 여부 확인
    boolean existsById_MemberIdAndId_ProductId(Long memberId, Long productId);
    
    // memberId와 productId로 조회
    Optional<Wishlist> findById_MemberIdAndId_ProductId(Long memberId, Long productId);
    
    // memberId와 productId로 삭제
    void deleteById_MemberIdAndId_ProductId(Long memberId, Long productId);
    
    // memberId로 전체 조회 (페이징 가능)
    Page<Wishlist> findAllById_MemberId(Long memberId, Pageable pageable);
    
    // memberId로 전체 조회 (페이징 없음)
    List<Wishlist> findAllById_MemberId(Long memberId);
}
