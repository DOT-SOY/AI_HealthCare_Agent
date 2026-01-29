package com.backend.service.memberinfo;

import com.backend.dto.memberinfo.MemberInfoAddrDTO;

import java.util.List;

public interface MemberInfoAddrService {

    // 배송지 목록 조회
    List<MemberInfoAddrDTO> getList(Long memberId);

    // 배송지 생성
    Long create(Long memberId, MemberInfoAddrDTO dto);

    // 배송지 수정
    MemberInfoAddrDTO update(Long id, MemberInfoAddrDTO dto);

    // 기본 배송지 설정
    MemberInfoAddrDTO setDefault(Long id);

    // 배송지 삭제
    void delete(Long id);
}


