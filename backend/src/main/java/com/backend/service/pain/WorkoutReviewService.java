package com.backend.service.pain;

public interface WorkoutReviewService {
    /**
     * 오늘 운동 회고를 시작합니다.
     * WebSocket을 통해 사용자에게 "오늘 운동은 어땠나요?" 메시지를 전송합니다.
     * 
     * @param memberId 회원 ID
     */
    void startWorkoutReview(Long memberId);
    
    /**
     * 통증을 처리합니다.
     * - 오늘 루틴의 운동과 관련된 통증인지 확인
     * - 관련된 통증: 완화 방법 제시 (RAG 기반)
     * - 관련 없는 통증: 관련 없다고 알려주고 완화 방법 제시 (자세 교정 등 포함, RAG 기반)
     * - DB에 저장하여 카운팅
     * - 주에 3회 이상 같은 부위 통증 시 더 디테일한 대처 방법 제시 (병원 권고 등)
     * 
     * @param memberId 회원 ID
     * @param bodyPart 통증 부위
     * @param description 통증 설명
     * @param intensity 통증 강도
     * @param isRelatedToExercise 오늘 루틴의 운동과 관련된 통증인지
     * @return AI 응답 메시지
     */
    String processPainReport(
        Long memberId, 
        String bodyPart, 
        String description, 
        int intensity, 
        boolean isRelatedToExercise
    );
}
