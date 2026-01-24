package com.backend.service;

import com.backend.dto.MemberBodyInfoRequestDto;
import com.backend.dto.MemberBodyInfoResponseDto;

import java.util.List;

public interface MemberBodyInfoService {
    
    /**
     * 신체 정보 생성
     */
    MemberBodyInfoResponseDto create(MemberBodyInfoRequestDto requestDto);
    
    /**
     * 신체 정보 조회 (ID로)
     */
    MemberBodyInfoResponseDto findById(Long id);
    
    /**
     * 회원별 신체 정보 전체 조회
     */
    List<MemberBodyInfoResponseDto> findByMemberId(String memberId);
    
    /**
     * 신체 정보 전체 조회
     */
    List<MemberBodyInfoResponseDto> findAll();
    
    /**
     * 신체 정보 수정
     */
    MemberBodyInfoResponseDto update(Long id, MemberBodyInfoRequestDto requestDto);
    
    /**
     * 신체 정보 삭제
     */
    void delete(Long id);
}
