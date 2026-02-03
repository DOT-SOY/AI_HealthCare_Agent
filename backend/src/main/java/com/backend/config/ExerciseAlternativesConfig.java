package com.backend.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 운동별 부상 위험 부위와 대체 운동 목록 (통증 부위별 루틴 수정용)
 * - 키: 운동명(한글), 값: { injuryRisks: [부위], alternatives: [대체운동명] }
 */
public final class ExerciseAlternativesConfig {

    private static final Map<String, ExerciseInfo> DATA = Map.ofEntries(
        // Upper Day
        entry("벤치프레스", list("어깨", "손목"), list("머신 체스트 프레스", "케이블 푸쉬다운")),
        entry("오버헤드프레스", list("어깨", "허리"), list("머신 체스트 프레스")),
        entry("바벨 컬", list("손목", "팔꿈치", "허리"), list("케이블 푸쉬다운")),
        entry("행잉레그레이즈", list("허리", "어깨"), list("데드버그", "글루트 브리지")),
        entry("플랭크", list("허리", "어깨"), list("데드버그", "글루트 브리지")),
        // Leg Day
        entry("스쿼트", list("무릎", "허리"), list("시티드 레그 컬", "레그 익스텐션")),
        entry("데드리프트", list("허리", "햄스트링"), list("시티드 레그 컬")),
        entry("힙쓰러스트", list("허리", "목"), list("글루트 브리지")),
        entry("카프레이즈", list("아킬레스건", "발목"), list()),
        entry("시티드 레그 컬", list("무릎", "햄스트링"), list()),
        entry("레그 익스텐션", list("무릎", "슬개건"), list()),
        // Core Day
        entry("데드버그", list(), list()),
        entry("글루트 브리지", list("무릎", "발목"), list())
    );

    public static Set<String> getExerciseNames() {
        return DATA.keySet();
    }

    public static ExerciseInfo get(String exerciseName) {
        if (exerciseName == null) return null;
        return DATA.get(exerciseName.trim());
    }

    /** 해당 부위가 부상 위험에 포함되는지 (한글 부위명 비교, 대소문자 무시) */
    public static boolean hasInjuryRisk(String exerciseName, String bodyPart) {
        ExerciseInfo info = get(exerciseName);
        if (info == null || bodyPart == null) return false;
        String normalized = bodyPart.trim().toLowerCase();
        return info.injuryRisks.stream()
            .anyMatch(r -> r != null && (r.toLowerCase().equals(normalized) || r.toLowerCase().contains(normalized) || normalized.contains(r.toLowerCase())));
    }

    public static List<String> getAlternatives(String exerciseName) {
        ExerciseInfo info = get(exerciseName);
        return info == null ? List.of() : info.alternatives;
    }

    private static Map.Entry<String, ExerciseInfo> entry(String name, List<String> risks, List<String> alts) {
        return Map.entry(name, new ExerciseInfo(risks, alts));
    }

    private static List<String> list(String... items) {
        return Stream.of(items).collect(Collectors.toList());
    }

    public static final class ExerciseInfo {
        public final List<String> injuryRisks;
        public final List<String> alternatives;

        public ExerciseInfo(List<String> injuryRisks, List<String> alternatives) {
            this.injuryRisks = injuryRisks;
            this.alternatives = alternatives;
        }
    }
}
