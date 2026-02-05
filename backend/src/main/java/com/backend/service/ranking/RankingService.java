package com.backend.service.ranking;

import com.backend.dto.response.RankingResponse;

public interface RankingService {

    /**
     * 최근 7일 기준 식단(EATEN)과 루틴(COMPLETED) 수행률로 랭킹을 계산합니다.
     *
     * 랭킹 그룹은 "현재 회원과 같은 성별, 같은 나이대(10대 단위), 같은 운동 목적"을 가진 사용자들로 고정됩니다.
     *
     * @param email 현재 로그인한 회원 이메일 (내 순위 및 그룹 기준 계산용)
     * @param limit 상위 몇 명까지 반환할지 (예: 10)
     */
    RankingResponse getRanking(String email, int limit);
}


