/**
 * 인바디 OCR 텍스트에서 체성분 수치 파싱
 * 인바디 기기별 출력 형식 차이를 고려해 다양한 패턴 지원
 */

const toNum = (v) => {
  if (v == null || v === "") return null;
  const n = Number(v);
  return isNaN(n) ? null : n;
};

/**
 * OCR 텍스트에서 인바디 수치 추출
 * @param {string} text - OCR로 추출된 텍스트
 * @returns {Object} 파싱된 체성분 데이터
 */
export function parseInbodyOcrText(text) {
  if (!text || typeof text !== "string") {
    return {};
  }
  const raw = text.replace(/\s+/g, " ").trim();
  const result = {};

  // 체중 (kg)
  const weightMatch = raw.match(/(?:체중|몸무게|Weight|weight)\s*[:]?\s*(\d+\.?\d*)\s*(?:kg)?/i)
    || raw.match(/(\d+\.?\d*)\s*kg\s*(?:체중|몸무게)?/i);
  if (weightMatch) result.weight = toNum(weightMatch[1]);

  // 체지방률 (%)
  const fatPctMatch = raw.match(/(?:체지방률|체지방\s*률|Body\s*Fat)\s*[:]?\s*(\d+\.?\d*)\s*%?/i)
    || raw.match(/(\d+\.?\d*)\s*%\s*(?:체지방률)?/i);
  if (fatPctMatch) result.bodyFatPercent = toNum(fatPctMatch[1]);

  // 골격근량 (kg)
  const muscleMatch = raw.match(/(?:골격근량|골격근|Skeletal\s*Muscle)\s*[:]?\s*(\d+\.?\d*)\s*(?:kg)?/i);
  if (muscleMatch) result.skeletalMuscleMass = toNum(muscleMatch[1]);

  // 체수분 (L)
  const waterMatch = raw.match(/(?:체수분|Body\s*Water)\s*[:]?\s*(\d+\.?\d*)\s*(?:L)?/i);
  if (waterMatch) result.bodyWater = toNum(waterMatch[1]);

  // 단백질 (kg)
  const proteinMatch = raw.match(/(?:단백질|Protein)\s*[:]?\s*(\d+\.?\d*)\s*(?:kg)?/i);
  if (proteinMatch) result.protein = toNum(proteinMatch[1]);

  // 무기질 (kg)
  const mineralsMatch = raw.match(/(?:무기질|Minerals)\s*[:]?\s*(\d+\.?\d*)\s*(?:kg)?/i);
  if (mineralsMatch) result.minerals = toNum(mineralsMatch[1]);

  // 체지방량 (kg)
  const fatMassMatch = raw.match(/(?:체지방량|체지방\s*량|Body\s*Fat\s*Mass)\s*[:]?\s*(\d+\.?\d*)\s*(?:kg)?/i);
  if (fatMassMatch) result.bodyFatMass = toNum(fatMassMatch[1]);

  // 숫자만 나열된 경우 순서로 추론
  const numbers = raw.match(/\d+\.?\d*/g);
  if (numbers && numbers.length >= 2 && result.weight == null && result.bodyFatPercent == null) {
    result.weight = toNum(numbers[0]);
    result.bodyFatPercent = toNum(numbers[1]);
    if (numbers[2]) result.skeletalMuscleMass = toNum(numbers[2]);
  }

  return result;
}

/**
 * 파싱된 두 객체가 동일한 데이터인지 비교 (주요 수치만)
 */
export function isSameBodyData(a, b) {
  if (!a || !b) return false;
  const keys = ["weight", "bodyFatPercent", "skeletalMuscleMass"];
  for (const k of keys) {
    const va = a[k] != null ? Number(a[k]) : null;
    const vb = b[k] != null ? Number(b[k]) : null;
    if (va !== vb) return false;
  }
  return true;
}
