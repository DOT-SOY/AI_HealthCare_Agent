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

    const hipAngle = (calculateAngle(leftHip, leftKnee, leftAnkle) + 
                      calculateAngle(rightHip, rightKnee, rightAnkle)) / 2;
    const kneeAngle = (calculateAngle(leftHip, leftKnee, leftAnkle) + 
                       calculateAngle(rightHip, rightKnee, rightAnkle)) / 2;

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

    const elbowAngle = (calculateAngle(leftShoulder, leftElbow, leftWrist) + 
                        calculateAngle(rightShoulder, rightElbow, rightWrist)) / 2;
    const chinHeight = (landmarks[LANDMARKS.NOSE].y + landmarks[LANDMARKS.LEFT_EYE].y) / 2;

    if (state === 'down') {
      // 내려가는 중 - 팔꿈치가 구부러지면 'up' 상태로
      if (elbowAngle <= counting.threshold.elbow_angle) {
        newState = 'up';
      }
    } else {
      // 올라가는 중 - 팔이 펴지고 턱이 바 위로 올라가면 카운트 +1
      if (elbowAngle >= counting.resetThreshold.elbow_angle && 
          chinHeight < leftShoulder.y) {
        newCount = 1;
        newState = 'down';
      }
    }
  } else if (exerciseName === '윗몸일으키기') {
    const leftShoulder = landmarks[LANDMARKS.LEFT_SHOULDER];
    const rightShoulder = landmarks[LANDMARKS.RIGHT_SHOULDER];
    const leftHip = landmarks[LANDMARKS.LEFT_HIP];
    const rightHip = landmarks[LANDMARKS.RIGHT_HIP];
    const leftKnee = landmarks[LANDMARKS.LEFT_KNEE];
    const rightKnee = landmarks[LANDMARKS.RIGHT_KNEE];
    const leftWrist = landmarks[LANDMARKS.LEFT_WRIST];
    const rightWrist = landmarks[LANDMARKS.RIGHT_WRIST];
    const nose = landmarks[LANDMARKS.NOSE];

    // 머리(코), 어깨, 손목의 Y 위치
    const headY = nose.y;
    const shoulderY = (leftShoulder.y + rightShoulder.y) / 2;
    const wristY = (leftWrist.y + rightWrist.y) / 2;
    
    // 엉덩이의 평균 Y 위치 (기준점)
    const hipY = (leftHip.y + rightHip.y) / 2;
    
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

    // 목 사용 체크 (간단한 휴리스틱)
    const neckAngle = calculateAngle(leftHip, leftShoulder, nose);
    if (neckAngle < rules.feedback.neck_strain.threshold) {
      feedbacks.push(rules.feedback.neck_strain.message);
    }
  }

  return feedbacks.length > 0 ? feedbacks[0] : null; // 첫 번째 피드백만 반환
}


