package com.backend.service.memberbodyinfo;

import com.backend.domain.memberbodyinfo.MemberBodyInfo;
import com.backend.dto.memberbodyinfo.MemberBodyInfoDTO;

import java.util.List;

public interface MemberBodyInfoService {

    /**
     * 신체 정보 생성
     */
    MemberBodyInfo create(MemberBodyInfoDTO requestDto);

    /**
     * 신체 정보 조회 (ID로)
     */
    MemberBodyInfo findById(Long id);

    /**
     * 회원별 신체 정보 전체 조회
     */
    List<MemberBodyInfo> findByMemberId(Long memberId);

    /**
     * 신체 정보 전체 조회
     */
    List<MemberBodyInfo> findAll();

    /**
     * 신체 정보 수정
     */
    MemberBodyInfo update(Long id, MemberBodyInfoDTO requestDto);

    /**
     * 신체 정보 삭제
     */
    void delete(Long id);
}