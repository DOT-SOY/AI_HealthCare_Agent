package com.backend.service.memberinfo;

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

    /**
     * 날짜와 항목으로 인바디 조회
     * @param memberId 회원 ID
     * @param date 날짜 (null이면 오늘)
     * @param metric 조회할 항목 (BODY_FAT/SKELETAL_MUSCLE/WEIGHT, null이면 모든 항목)
     * @return 인바디 정보 (항목별로 필터링된 값만 포함)
     */
    MemberInfoBodyResponseDTO getBodyInfoByDateAndMetric(Long memberId, java.time.LocalDate date, String metric);
}


