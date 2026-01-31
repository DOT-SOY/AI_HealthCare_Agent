package com.backend.service.memberinfo;

import com.backend.dto.memberinfo.BodyCompareFeedbackDTO;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;

import java.util.List;

public interface MemberInfoBodyService {

    // 신체 정보 생성
    Long create(Long memberId, MemberInfoBodyDTO dto);

    // 신체 정보 수정
    MemberInfoBodyResponseDTO update(Long id, MemberInfoBodyDTO dto);

    // 신체 정보 삭제
    void delete(Long id);

    // 특정 회원의 신체 정보 이력 조회
    List<MemberInfoBodyResponseDTO> getHistory(Long memberId);

    // 특정 회원의 최신 신체 정보 조회
    MemberInfoBodyResponseDTO getLatest(Long memberId);

    /** OCR 결과 저장 후 직전 1 row와만 비교하여 규칙 기반 피드백 반환 (7일치 식단/운동 없음) */
    BodyCompareFeedbackDTO saveAndCompare(Long memberId, MemberInfoBodyDTO dto);
}


