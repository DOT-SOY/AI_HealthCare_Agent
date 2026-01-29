package com.backend.service.pain;

import com.backend.dto.response.PainAdviceResponse;

public interface PainService {
    /**
     * 통증을 보고하고, 에스컬레이션 기준이 되는 누적 횟수를 계산합니다.
     * - 기본 기준: 최근 7일 내 동일 부위 통증 횟수
     * - 주에 3회 이상이면 HIGH 레벨로 처리
     * 
     * @param memberId 회원 ID
     * @param area 통증 부위
     * @param intensity 통증 강도
     * @param description 통증 설명
     * @param isRelatedToExercise 운동과 관련된 통증인지
     * @return 에스컬레이션 카운트 (최근 7일 내 동일 부위 통증 횟수)
     */
    long reportPain(Long memberId, String area, int intensity, String description, boolean isRelatedToExercise);
    
    /**
     * 최근 7일 내 동일 부위 통증 횟수를 계산합니다.
     * 
     * @param memberId 회원 ID
     * @param area 통증 부위
     * @return 최근 7일 내 통증 횟수
     */
    long calculateEscalationCount(Long memberId, String area);
    
    /**
     * Python AI 서버에 통증 조언을 요청합니다.
     * RAG를 통해 운동별 자극 부위 및 각 부위별 통증 대처 방법을 제공합니다.
     * 
     * @param area 통증 부위
     * @param count 최근 7일 내 통증 횟수
     * @param description 통증 설명
     * @return AI 조언 응답
     */
    PainAdviceResponse getPainAdvice(String area, long count, String description);
}
