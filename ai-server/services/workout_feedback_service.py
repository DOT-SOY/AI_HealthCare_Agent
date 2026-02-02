"""
운동 세션 피드백 서비스
"""
from services.ai_service import call_ai
from prompts.workout_feedback import SYSTEM_PROMPT


def generate_workout_feedback(
    exercise_type: str,
    total_reps: int,
    duration_sec: int,
    main_issue: str,
    bad_posture_ratio: float
) -> str:
    """운동 세션 결과를 바탕으로 피드백 생성"""
    
    user_prompt = f"""운동 세션 결과:
- 운동 종류: {exercise_type}
- 총 운동 횟수: {total_reps}회
- 운동 시간: {duration_sec}초
- 주요 자세 문제: {main_issue}
- 자세 오류 비율: {bad_posture_ratio}%

위의 운동 세션 결과를 바탕으로 사용자에게 보여줄 피드백 문구를 작성하라."""
    
    try:
        feedback = call_ai(
            system_prompt=SYSTEM_PROMPT,
            user_prompt=user_prompt,
            temperature=0.7
        )
        return feedback.strip()
    except Exception as e:
        print(f"피드백 생성 실패: {e}")
        return f"{exercise_type} {total_reps}회를 {duration_sec}초 동안 수행하셨네요. 오늘 운동을 완료하셨나요?"


