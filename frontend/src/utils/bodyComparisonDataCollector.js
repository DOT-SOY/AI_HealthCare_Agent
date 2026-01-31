/**
 * 인바디 비교 분석용 데이터 수집
 * - 2장: 1번(이전) vs 2번(현재) 비교
 * - 1장: 이전 데이터(latestInfo) 있으면 비교, 없으면 분석만
 */

import { mealApi } from "../api/mealApi";
import { routineApi } from "../api/routineApi";

function formatDateStr(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/**
 * 최근 7일 식단 데이터 수집 (날짜별 대시보드 호출 후 집계)
 */
async function collectMealData(days = 7) {
  const today = new Date();
  let totalCal = 0;
  let totalCarbs = 0;
  let totalProtein = 0;
  let totalFat = 0;
  let goalCal = 0;
  let count = 0;
  try {
    for (let i = 0; i < days; i++) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const dateStr = formatDateStr(d);
      const dash = await mealApi.getDashboard(dateStr);
      if (dash?.calories) {
        totalCal += dash.calories.current ?? 0;
        goalCal += dash.calories.goal ?? 0;
        count++;
      }
      if (dash?.carbs) totalCarbs += dash.carbs.current ?? 0;
      if (dash?.protein) totalProtein += dash.protein.current ?? 0;
      if (dash?.fat) totalFat += dash.fat.current ?? 0;
    }
  } catch (e) {
    console.warn("식단 데이터 수집 실패:", e);
    return null;
  }
  const n = count || 1;
  return {
    avgCalories: Math.round(totalCal / n),
    goalCalories: Math.round(goalCal / n),
    avgCarbs: Math.round(totalCarbs / n),
    avgProtein: Math.round(totalProtein / n),
    avgFat: Math.round(totalFat / n),
  };
}

/**
 * 최근 운동 이력 수집
 */
async function collectExerciseData() {
  try {
    const history = await routineApi.getHistory();
    const list = Array.isArray(history) ? history : [];
    const recent = list.slice(0, 30);
    const totalWorkouts = recent.length;
    let totalDuration = 0;
    recent.forEach((r) => {
      totalDuration += r.duration ?? r.workoutDuration ?? 0;
    });
    return {
      totalWorkouts,
      avgWorkoutDuration: totalWorkouts ? Math.round(totalDuration / totalWorkouts) : 0,
      exercisesCompleted: totalWorkouts,
    };
  } catch (e) {
    console.warn("운동 데이터 수집 실패:", e);
    return null;
  }
}

/**
 * 비교 분석용 데이터 수집
 * @param {Object} opts
 * @param {Array<Object>} opts.ocrParsedList - OCR 파싱 결과 1~2개 (순서: [이전, 현재] 또는 [현재])
 * @param {Object|null} opts.latestInfo - 서버 직전 인바디 (1장일 때만 사용)
 * @param {Array} opts.historyData - 인바디 이력 (기간 계산 등에 사용 가능)
 */
export async function collectComparisonData({ ocrParsedList, latestInfo, historyData = [] }) {
  const periodDays = 7;
  const endDate = new Date();
  const startDate = new Date();
  startDate.setDate(startDate.getDate() - periodDays);
  const period = {
    startDate: formatDateStr(startDate),
    endDate: formatDateStr(endDate),
    days: periodDays,
  };

  let previousBodyInfo = null;
  let currentBodyInfo = null;

  if (ocrParsedList.length === 2) {
    previousBodyInfo = ocrParsedList[0] || null;
    currentBodyInfo = ocrParsedList[1] || null;
  } else if (ocrParsedList.length === 1) {
    currentBodyInfo = ocrParsedList[0] || null;
    if (latestInfo && typeof latestInfo === "object") {
      previousBodyInfo = {
        weight: latestInfo.weight,
        bodyFatPercent: latestInfo.bodyFatPercent,
        skeletalMuscleMass: latestInfo.skeletalMuscleMass,
        bodyWater: latestInfo.bodyWater,
        protein: latestInfo.protein,
        minerals: latestInfo.minerals,
        bodyFatMass: latestInfo.bodyFatMass,
        measuredTime: latestInfo.measuredTime,
      };
    }
  }

  const [mealData, exerciseData] = await Promise.all([
    collectMealData(periodDays),
    collectExerciseData(),
  ]);

  return {
    previousBodyInfo,
    currentBodyInfo,
    mealData,
    exerciseData,
    period,
  };
}
