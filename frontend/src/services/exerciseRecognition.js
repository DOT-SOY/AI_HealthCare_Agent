import exerciseRules from '../data/exerciseRules.json';

/**
 * MediaPipe 랜드마크 인덱스
 */
const LANDMARKS = {
  LEFT_SHOULDER: 11,
  RIGHT_SHOULDER: 12,
  LEFT_ELBOW: 13,
  RIGHT_ELBOW: 14,
  LEFT_WRIST: 15,
  RIGHT_WRIST: 16,
  LEFT_HIP: 23,
  RIGHT_HIP: 24,
  LEFT_KNEE: 25,
  RIGHT_KNEE: 26,
  LEFT_ANKLE: 27,
  RIGHT_ANKLE: 28,
  NOSE: 0,
  LEFT_EYE: 2,
  RIGHT_EYE: 5
};

/**
 * 관절별 신뢰도 임계값
 */
const JOINT_CONFIDENCE_THRESHOLDS = {
  TORSO: 0.6,      // 몸통 관절 (어깨, 엉덩이)
  HAND_FOOT: 0.4,  // 손/발 관절
  DEFAULT: 0.5     // 기타 관절
};

/**
 * 관절 분류
 */
const JOINT_CATEGORIES = {
  TORSO: [LANDMARKS.LEFT_SHOULDER, LANDMARKS.RIGHT_SHOULDER, LANDMARKS.LEFT_HIP, LANDMARKS.RIGHT_HIP],
  HAND_FOOT: [LANDMARKS.LEFT_WRIST, LANDMARKS.RIGHT_WRIST, LANDMARKS.LEFT_ANKLE, LANDMARKS.RIGHT_ANKLE]
};

/**
 * 관절의 신뢰도 임계값을 반환합니다.
 * 
 * @param {number} landmarkIndex - 랜드마크 인덱스
 * @returns {number} - 신뢰도 임계값
 */
function getConfidenceThreshold(landmarkIndex) {
  if (JOINT_CATEGORIES.TORSO.includes(landmarkIndex)) {
    return JOINT_CONFIDENCE_THRESHOLDS.TORSO;
  }
  if (JOINT_CATEGORIES.HAND_FOOT.includes(landmarkIndex)) {
    return JOINT_CONFIDENCE_THRESHOLDS.HAND_FOOT;
  }
  return JOINT_CONFIDENCE_THRESHOLDS.DEFAULT;
}

/**
 * keyPoint 문자열을 랜드마크 인덱스로 변환
 */
function keyPointToLandmarkIndex(keyPoint) {
  const mapping = {
    'left_shoulder': LANDMARKS.LEFT_SHOULDER,
    'right_shoulder': LANDMARKS.RIGHT_SHOULDER,
    'left_elbow': LANDMARKS.LEFT_ELBOW,
    'right_elbow': LANDMARKS.RIGHT_ELBOW,
    'left_wrist': LANDMARKS.LEFT_WRIST,
    'right_wrist': LANDMARKS.RIGHT_WRIST,
    'left_hip': LANDMARKS.LEFT_HIP,
    'right_hip': LANDMARKS.RIGHT_HIP,
    'left_knee': LANDMARKS.LEFT_KNEE,
    'right_knee': LANDMARKS.RIGHT_KNEE,
    'left_ankle': LANDMARKS.LEFT_ANKLE,
    'right_ankle': LANDMARKS.RIGHT_ANKLE,
    'nose': LANDMARKS.NOSE,
    'left_eye': LANDMARKS.LEFT_EYE,
    'right_eye': LANDMARKS.RIGHT_EYE
  };
  return mapping[keyPoint.toLowerCase()];
}

/**
 * 운동별 핵심 관절 검증
 * 
 * exerciseRules.json의 detection.keyPoints를 활용하여
 * 각 운동에 필요한 핵심 관절의 유효성을 검증합니다.
 * 모든 관절이 필요하지 않고, 대부분의 핵심 관절이 유효하면 분석을 진행합니다.
 * 
 * @param {Array} landmarks - 랜드마크 배열
 * @param {string} exerciseName - 운동 이름
 * @returns {boolean} - 핵심 관절이 충분히 유효한지 여부
 */
export function validateKeyJoints(landmarks, exerciseName) {
  if (!landmarks || landmarks.length === 0) {
    return false;
  }

  const rules = exerciseRules[exerciseName];
  if (!rules || !rules.detection || !rules.detection.keyPoints) {
    // 규칙이 없으면 기본 검증 (최소 33개 랜드마크)
    return landmarks.length >= 33;
  }

  const keyPoints = rules.detection.keyPoints;
  let validCount = 0;
  let totalCount = 0;
  
  // 핵심 관절 중 유효한 관절의 비율 확인
  for (const keyPoint of keyPoints) {
    const landmarkIndex = keyPointToLandmarkIndex(keyPoint);
    
    if (landmarkIndex === undefined) {
      // 매핑되지 않은 keyPoint는 무시
      continue;
    }

    totalCount++;
    const landmark = landmarks[landmarkIndex];
    
    // 랜드마크가 없거나 null이면 무효
    if (!landmark) {
      continue;
    }

    // 신뢰도 검증
    const threshold = getConfidenceThreshold(landmarkIndex);
    const visibility = landmark.visibility !== undefined ? landmark.visibility : 1.0;
    
    if (visibility >= threshold) {
      validCount++;
    }
  }

  // 핵심 관절의 50% 이상이 유효하면 분석 진행
  // 최소 2개 이상의 핵심 관절이 유효해야 함
  const minRequiredRatio = 0.5;
  const minRequiredCount = Math.max(2, Math.ceil(totalCount * minRequiredRatio));
  
  return validCount >= minRequiredCount;
}

/**
 * 관절별 신뢰도 기반 필터링
 * 
 * 각 관절의 visibility(신뢰도)를 확인하여
 * 임계값 미만인 관절은 null로 처리합니다.
 * 
 * 주의: 필터링은 분석 시에만 사용되며, 랜드마크 그리기에는 원본을 사용합니다.
 * 
 * @param {Array} landmarks - 원본 랜드마크 배열
 * @returns {Array} - 필터링된 랜드마크 배열 (신뢰도 낮은 관절은 null)
 */
export function filterLandmarksByConfidence(landmarks) {
  if (!landmarks || landmarks.length === 0) {
    return landmarks;
  }

  return landmarks.map((landmark, index) => {
    if (!landmark) {
      return null;
    }

    const threshold = getConfidenceThreshold(index);
    const visibility = landmark.visibility !== undefined ? landmark.visibility : 1.0;

    // 신뢰도가 임계값 미만이면 null 반환
    // 하지만 분석 함수들은 null 관절을 안전하게 처리할 수 있어야 함
    if (visibility < threshold) {
      return null;
    }

    return landmark;
  });
}

/**
 * 3점 각도 계산 (라디안)
 */
function calculateAngle(point1, point2, point3) {
  const radians = Math.atan2(point3.y - point2.y, point3.x - point2.x) -
                  Math.atan2(point1.y - point2.y, point1.x - point2.x);
  let angle = Math.abs(radians * 180.0 / Math.PI);
  if (angle > 180.0) {
    angle = 360 - angle;
  }
  return angle;
}

/**
 * 운동별 카운팅 로직
 */
export function countRep(landmarks, exerciseName, previousState) {
  if (!landmarks || landmarks.length < 33) {
    return { count: 0, state: previousState || 'down' };
  }

  const rules = exerciseRules[exerciseName];
  if (!rules || !rules.counting) {
    return { count: 0, state: previousState || 'down' };
  }

  const counting = rules.counting;
  const state = previousState || 'down';
  let newCount = 0;
  let newState = state;

  if (exerciseName === '스쿼트') {
    const leftHip = landmarks[LANDMARKS.LEFT_HIP];
    const rightHip = landmarks[LANDMARKS.RIGHT_HIP];
    const leftKnee = landmarks[LANDMARKS.LEFT_KNEE];
    const rightKnee = landmarks[LANDMARKS.RIGHT_KNEE];
    const leftAnkle = landmarks[LANDMARKS.LEFT_ANKLE];
    const rightAnkle = landmarks[LANDMARKS.RIGHT_ANKLE];

    // 양쪽 중 하나라도 유효하면 사용, 둘 다 없으면 스킵
    const leftSideValid = leftHip && leftKnee && leftAnkle;
    const rightSideValid = rightHip && rightKnee && rightAnkle;
    
    if (!leftSideValid && !rightSideValid) {
      return { count: 0, state: previousState || 'down' };
    }

    // 유효한 쪽만 사용하거나 양쪽 평균
    let hipAngle, kneeAngle;
    if (leftSideValid && rightSideValid) {
      hipAngle = (calculateAngle(leftHip, leftKnee, leftAnkle) + 
                  calculateAngle(rightHip, rightKnee, rightAnkle)) / 2;
      kneeAngle = (calculateAngle(leftHip, leftKnee, leftAnkle) + 
                   calculateAngle(rightHip, rightKnee, rightAnkle)) / 2;
    } else if (leftSideValid) {
      hipAngle = calculateAngle(leftHip, leftKnee, leftAnkle);
      kneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle);
    } else {
      hipAngle = calculateAngle(rightHip, rightKnee, rightAnkle);
      kneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle);
    }

    if (state === 'down') {
      // 내려가는 중 - 임계값 이하로 내려가면 'up' 상태로
      if (hipAngle <= counting.threshold.hip_angle && kneeAngle <= counting.threshold.knee_angle) {
        newState = 'up';
      }
    } else {
      // 올라가는 중 - 리셋 임계값 이상으로 올라가면 카운트 +1
      if (hipAngle >= counting.resetThreshold.hip_angle && 
          kneeAngle >= counting.resetThreshold.knee_angle) {
        newCount = 1;
        newState = 'down';
      }
    }
  } else if (exerciseName === '턱걸이') {
    const leftShoulder = landmarks[LANDMARKS.LEFT_SHOULDER];
    const rightShoulder = landmarks[LANDMARKS.RIGHT_SHOULDER];
    const leftElbow = landmarks[LANDMARKS.LEFT_ELBOW];
    const rightElbow = landmarks[LANDMARKS.RIGHT_ELBOW];
    const leftWrist = landmarks[LANDMARKS.LEFT_WRIST];
    const rightWrist = landmarks[LANDMARKS.RIGHT_WRIST];
    const nose = landmarks[LANDMARKS.NOSE];
    const leftEye = landmarks[LANDMARKS.LEFT_EYE];

    // 양쪽 팔 중 하나라도 유효하면 사용
    const leftArmValid = leftShoulder && leftElbow && leftWrist;
    const rightArmValid = rightShoulder && rightElbow && rightWrist;
    const headValid = nose && leftEye;
    
    if ((!leftArmValid && !rightArmValid) || !headValid) {
      return { count: 0, state: previousState || 'down' };
    }

    // 유효한 쪽만 사용하거나 양쪽 평균
    let elbowAngle, chinHeight, shoulderY;
    if (leftArmValid && rightArmValid) {
      elbowAngle = (calculateAngle(leftShoulder, leftElbow, leftWrist) + 
                    calculateAngle(rightShoulder, rightElbow, rightWrist)) / 2;
      shoulderY = (leftShoulder.y + rightShoulder.y) / 2;
    } else if (leftArmValid) {
      elbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist);
      shoulderY = leftShoulder.y;
    } else {
      elbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist);
      shoulderY = rightShoulder.y;
    }
    chinHeight = (nose.y + leftEye.y) / 2;

    if (state === 'down') {
      // 내려가는 중 - 팔꿈치가 구부러지면 'up' 상태로
      if (elbowAngle <= counting.threshold.elbow_angle) {
        newState = 'up';
      }
    } else {
      // 올라가는 중 - 팔이 펴지고 턱이 바 위로 올라가면 카운트 +1
      if (elbowAngle >= counting.resetThreshold.elbow_angle && 
          chinHeight < shoulderY) {
        newCount = 1;
        newState = 'down';
      }
    }
  } else if (exerciseName === '윗몸일으키기') {
    const leftShoulder = landmarks[LANDMARKS.LEFT_SHOULDER];
    const rightShoulder = landmarks[LANDMARKS.RIGHT_SHOULDER];
    const leftHip = landmarks[LANDMARKS.LEFT_HIP];
    const rightHip = landmarks[LANDMARKS.RIGHT_HIP];
    const leftWrist = landmarks[LANDMARKS.LEFT_WRIST];
    const rightWrist = landmarks[LANDMARKS.RIGHT_WRIST];
    const nose = landmarks[LANDMARKS.NOSE];

    // 최소한 어깨, 엉덩이, 코는 필요
    const shoulderValid = leftShoulder || rightShoulder;
    const hipValid = leftHip || rightHip;
    
    if (!shoulderValid || !hipValid || !nose) {
      return { count: 0, state: previousState || 'down' };
    }

    // 머리(코), 어깨, 손목의 Y 위치
    const headY = nose.y;
    const shoulderY = leftShoulder && rightShoulder 
      ? (leftShoulder.y + rightShoulder.y) / 2
      : (leftShoulder ? leftShoulder.y : rightShoulder.y);
    const wristY = leftWrist && rightWrist
      ? (leftWrist.y + rightWrist.y) / 2
      : (leftWrist ? leftWrist.y : (rightWrist ? rightWrist.y : shoulderY));
    
    // 엉덩이의 평균 Y 위치 (기준점)
    const hipY = leftHip && rightHip
      ? (leftHip.y + rightHip.y) / 2
      : (leftHip ? leftHip.y : rightHip.y);
    
    // 머리, 어깨, 손목 중 가장 위에 있는 위치 (상체의 최상단)
    const topBodyY = Math.min(headY, shoulderY, wristY);
    
    // 히스테리시스 적용: 올라갈 때와 내려올 때 다른 임계값 사용
    const upThreshold = 0.12;   // 올라갈 때: 엉덩이보다 0.12 이상 위로 올라가야 함
    const downThreshold = 0.06;  // 내려올 때: 엉덩이보다 0.06 이내로 내려와야 함

    if (state === 'down') {
      // 누운 상태 - 상체의 최상단이 엉덩이보다 충분히 위로 올라가면 'up' 상태로
      if (topBodyY < hipY - upThreshold) {
        newState = 'up';
      } else {
        newState = 'down';
      }
    } else {
      // 올라간 상태 - 상체의 최상단이 엉덩이 근처로 충분히 내려오면 카운트 +1
      if (topBodyY >= hipY - downThreshold) {
        newCount = 1;
        newState = 'down';
      } else {
        newState = 'up';
      }
    }
  }

  return { count: newCount, state: newState };
}

/**
 * 실시간 피드백 생성
 */
export function generateFeedback(landmarks, exerciseName) {
  if (!landmarks || landmarks.length < 33) {
    return null;
  }

  const rules = exerciseRules[exerciseName];
  if (!rules || !rules.feedback) {
    return null;
  }

  const feedbacks = [];

  if (exerciseName === '스쿼트') {
    const leftHip = landmarks[LANDMARKS.LEFT_HIP];
    const rightHip = landmarks[LANDMARKS.RIGHT_HIP];
    const leftKnee = landmarks[LANDMARKS.LEFT_KNEE];
    const rightKnee = landmarks[LANDMARKS.RIGHT_KNEE];
    const leftAnkle = landmarks[LANDMARKS.LEFT_ANKLE];
    const rightAnkle = landmarks[LANDMARKS.RIGHT_ANKLE];
    const leftShoulder = landmarks[LANDMARKS.LEFT_SHOULDER];
    const rightShoulder = landmarks[LANDMARKS.RIGHT_SHOULDER];

    // 필요한 관절이 모두 유효한지 확인
    if (!leftHip || !rightHip || !leftKnee || !rightKnee || 
        !leftAnkle || !rightAnkle || !leftShoulder || !rightShoulder) {
      return null;
    }

    // 허리 숙여짐 체크
    const backAngle = calculateAngle(leftShoulder, leftHip, leftKnee);
    if (backAngle > rules.feedback.back_leaning.threshold) {
      feedbacks.push(rules.feedback.back_leaning.message);
    }

    // 무릎 각도 체크
    const kneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle);
    if (kneeAngle < rules.feedback.knee_too_bent.threshold) {
      feedbacks.push(rules.feedback.knee_too_bent.message);
    }
    
    // 문제가 없으면 긍정 피드백
    if (feedbacks.length === 0) {
      // 자세가 좋을 때 (허리가 곧고 무릎 각도가 적절할 때)
      if (backAngle <= 160 && kneeAngle >= 70 && kneeAngle <= 120) {
        feedbacks.push("좋습니다! 자세가 정확합니다.");
      }
    }
  } else if (exerciseName === '턱걸이') {
    const leftShoulder = landmarks[LANDMARKS.LEFT_SHOULDER];
    const rightShoulder = landmarks[LANDMARKS.RIGHT_SHOULDER];
    const leftElbow = landmarks[LANDMARKS.LEFT_ELBOW];
    const rightElbow = landmarks[LANDMARKS.RIGHT_ELBOW];
    const leftWrist = landmarks[LANDMARKS.LEFT_WRIST];
    const rightWrist = landmarks[LANDMARKS.RIGHT_WRIST];
    const nose = landmarks[LANDMARKS.NOSE];

    // 필요한 관절이 모두 유효한지 확인
    if (!leftShoulder || !rightShoulder || !leftElbow || !rightElbow || 
        !leftWrist || !rightWrist || !nose) {
      return null;
    }

    // 불완전한 풀업 체크
    if (nose.y > leftShoulder.y) {
      feedbacks.push(rules.feedback.incomplete_pull.message);
    }

    // 팔꿈치 각도 체크 (양쪽 평균)
    const leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist);
    const rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist);
    const elbowAngle = (leftElbowAngle + rightElbowAngle) / 2;
    
    // 팔이 완전히 펴졌는지 확인
    if (elbowAngle < rules.feedback.not_full_extension.threshold) {
      feedbacks.push(rules.feedback.not_full_extension.message);
    } else if (elbowAngle >= 170) {
      // 팔이 완전히 펴졌을 때 긍정 피드백 (문제가 없을 때만)
      if (feedbacks.length === 0) {
        feedbacks.push("좋습니다! 팔을 완전히 펴셨네요.");
      }
    }
  } else if (exerciseName === '윗몸일으키기') {
    const leftShoulder = landmarks[LANDMARKS.LEFT_SHOULDER];
    const rightShoulder = landmarks[LANDMARKS.RIGHT_SHOULDER];
    const leftHip = landmarks[LANDMARKS.LEFT_HIP];
    const nose = landmarks[LANDMARKS.NOSE];

    // 필요한 관절이 모두 유효한지 확인
    if (!leftShoulder || !rightShoulder || !leftHip || !nose) {
      return null;
    }

    // 목 사용 체크 (간단한 휴리스틱)
    const neckAngle = calculateAngle(leftHip, leftShoulder, nose);
    if (neckAngle < rules.feedback.neck_strain.threshold) {
      feedbacks.push(rules.feedback.neck_strain.message);
    }
  }

  return feedbacks.length > 0 ? feedbacks[0] : null; // 첫 번째 피드백만 반환
}


