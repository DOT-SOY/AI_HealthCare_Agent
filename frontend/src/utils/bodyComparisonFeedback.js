/**
 * 인바디 비교/분석 규칙 기반 피드백 생성
 * - 2장 또는 1장+직전: 비교 분석
 * - 1장만(직전 없음): 분석만
 */

const num = (v) => (v != null && v !== "" ? Number(v) : null);

/**
 * 비교 분석 또는 단일 분석 피드백 생성
 * @param {Object} collected - collectComparisonData 반환값
 */
export function buildComparisonFeedback(collected) {
  const { previousBodyInfo, currentBodyInfo, mealData, exerciseData, period } = collected || {};
  const bodyChanges = [];
  let summary = "";
  let mealFeedback = "";
  let exerciseFeedback = "";
  const recommendations = [];

  const hasComparison = previousBodyInfo && currentBodyInfo;

  if (hasComparison) {
    // --- 체중 변화 ---
    const wPrev = num(previousBodyInfo.weight);
    const wCur = num(currentBodyInfo.weight);
    if (wPrev != null && wCur != null) {
      const diff = wCur - wPrev;
      if (Math.abs(diff) >= 0.5) {
        const dir = diff > 0 ? "증가" : "감소";
        bodyChanges.push({
          type: "weight",
          change: dir,
          value: `${diff > 0 ? "+" : ""}${diff.toFixed(1)}kg`,
          message: `체중 ${dir} (${wPrev}kg → ${wCur}kg)`,
        });
      }
    }

    // --- 체지방률 변화 ---
    const fPrev = num(previousBodyInfo.bodyFatPercent);
    const fCur = num(currentBodyInfo.bodyFatPercent);
    if (fPrev != null && fCur != null) {
      const diff = fCur - fPrev;
      if (Math.abs(diff) >= 0.5) {
        const dir = diff > 0 ? "증가" : "감소";
        bodyChanges.push({
          type: "bodyFatPercent",
          change: dir,
          value: `${diff > 0 ? "+" : ""}${diff.toFixed(1)}%`,
          message: `체지방률 ${dir} (${fPrev}% → ${fCur}%)`,
        });
        if (diff > 0) recommendations.push("유산소 운동을 꾸준히 해보세요.");
        else recommendations.push("체지방률이 줄어든 좋은 변화입니다.");
      }
    }

    // --- 골격근량 변화 ---
    const mPrev = num(previousBodyInfo.skeletalMuscleMass);
    const mCur = num(currentBodyInfo.skeletalMuscleMass);
    if (mPrev != null && mCur != null) {
      const diff = mCur - mPrev;
      if (Math.abs(diff) >= 0.2) {
        const dir = diff > 0 ? "증가" : "감소";
        bodyChanges.push({
          type: "skeletalMuscleMass",
          change: dir,
          value: `${diff > 0 ? "+" : ""}${diff.toFixed(1)}kg`,
          message: `골격근량 ${dir} (${mPrev}kg → ${mCur}kg)`,
        });
        if (diff > 0) recommendations.push("근육량이 늘었습니다. 꾸준히 유지해보세요.");
        else recommendations.push("단백질 섭취와 근력 운동을 함께 해보세요.");
      }
    }

    summary =
      bodyChanges.length > 0
        ? `이전 대비 ${bodyChanges.length}가지 체성분 변화가 있습니다.`
        : "이전 측정과 큰 변화는 없습니다.";
  } else {
    // 1장만 분석 (직전 없음)
    if (currentBodyInfo) {
      const w = num(currentBodyInfo.weight);
      const f = num(currentBodyInfo.bodyFatPercent);
      const m = num(currentBodyInfo.skeletalMuscleMass);
      const parts = [];
      if (w != null) parts.push(`체중 ${w}kg`);
      if (f != null) parts.push(`체지방률 ${f}%`);
      if (m != null) parts.push(`골격근량 ${m}kg`);
      summary = parts.length ? `현재 측정: ${parts.join(", ")}.` : "측정값을 확인해보세요.";
    } else {
      summary = "분석할 수치를 추출하지 못했습니다.";
    }
  }

  // --- 식단 ---
  if (mealData) {
    const { avgCalories, goalCalories } = mealData;
    if (goalCalories > 0) {
      const pct = (avgCalories / goalCalories) * 100;
      if (pct > 110) {
        mealFeedback = "최근 칼로리가 목표보다 높습니다. 식사량을 조절해보세요.";
        recommendations.push("일일 칼로리를 목표 수준으로 맞춰보세요.");
      } else if (pct < 90) {
        mealFeedback = "최근 칼로리가 목표보다 낮습니다. 균형 잡힌 식사를 권장합니다.";
        recommendations.push("필요한 영양소를 충분히 섭취해보세요.");
      } else {
        mealFeedback = "최근 식단이 목표 칼로리와 비슷하게 유지되고 있습니다.";
      }
    } else {
      mealFeedback = `최근 평균 칼로리 약 ${avgCalories ?? 0} kcal입니다.`;
    }
  } else {
    mealFeedback = "식단 데이터를 불러오지 못했습니다.";
  }

  // --- 운동 ---
  if (exerciseData) {
    const { totalWorkouts } = exerciseData;
    if (totalWorkouts < 3) {
      exerciseFeedback = "최근 운동 빈도가 적습니다. 주 3회 이상을 권장합니다.";
      recommendations.push("운동 빈도를 조금씩 늘려보세요.");
    } else {
      exerciseFeedback = "최근 꾸준히 운동하고 있습니다. 좋은 습관을 유지해보세요.";
    }
  } else {
    exerciseFeedback = "운동 데이터를 불러오지 못했습니다.";
  }

  return {
    summary,
    bodyChanges,
    mealFeedback,
    exerciseFeedback,
    recommendations: [...new Set(recommendations)],
    hasComparison,
  };
}
