/**
 * MediaPipe Pose 트래킹 안정화 유틸리티
 * 
 * 일시적인 트래킹 끊김 문제를 해결하기 위해
 * 마지막 유효 랜드마크를 일정 프레임 동안 유지합니다.
 */

const MAX_MISSING_FRAMES = 5; // 최대 누락 프레임 수

/**
 * 랜드마크 안정화 클래스
 */
export class PoseStabilizer {
  constructor() {
    this.lastValidLandmarks = null;
    this.missingFrameCount = 0;
  }

  /**
   * 랜드마크를 안정화합니다.
   * null인 경우 마지막 유효 랜드마크를 반환하되,
   * MAX_MISSING_FRAMES를 초과하면 null을 반환합니다.
   * 
   * @param {Array|null} landmarks - 현재 프레임의 랜드마크
   * @returns {Array|null} - 안정화된 랜드마크 또는 null
   */
  stabilizeLandmarks(landmarks) {
    if (landmarks && landmarks.length > 0) {
      // 유효한 랜드마크가 있으면 저장하고 카운터 리셋
      this.lastValidLandmarks = JSON.parse(JSON.stringify(landmarks)); // deep copy
      this.missingFrameCount = 0;
      return landmarks;
    }

    // 랜드마크가 null이거나 비어있는 경우
    this.missingFrameCount++;

    if (this.missingFrameCount <= MAX_MISSING_FRAMES && this.lastValidLandmarks) {
      // 마지막 유효 랜드마크 반환
      return JSON.parse(JSON.stringify(this.lastValidLandmarks)); // deep copy
    }

    // MAX_MISSING_FRAMES 초과 시 null 반환
    return null;
  }

  /**
   * 누락 프레임 수를 반환합니다.
   * 
   * @returns {number} - 누락 프레임 수
   */
  getMissingFrameCount() {
    return this.missingFrameCount;
  }

  /**
   * 안정화 상태를 리셋합니다.
   */
  reset() {
    this.lastValidLandmarks = null;
    this.missingFrameCount = 0;
  }
}

