"""
Commerce 대화 상태머신 (Redis 저장소 사용, SSOT)
"""
from typing import Optional
from datetime import datetime

from .commerce_types import CommerceState, SessionData
from .commerce_session_store import get as store_get, set as store_set, delete as store_delete

SESSION_TTL_SEC = 1800  # 30분


class CommerceStateMachine:
    """
    Commerce 대화 상태머신 (Redis 저장소, TTL 30분)
    get_session은 조회만 수행. 만료 판정은 handle_commerce_recommend 진입 직후에서만 수행.
    """
    def get_session(self, session_id: str) -> Optional[SessionData]:
        """세션 데이터 조회 (만료 삭제는 하지 않음)"""
        return store_get(session_id)

    def create_session(self, session_id: str) -> SessionData:
        """새 세션 생성"""
        session_data = SessionData(state=CommerceState.RECOMMEND)
        store_set(session_id, session_data, SESSION_TTL_SEC)
        return session_data

    def update_session(self, session_id: str, **kwargs) -> Optional[SessionData]:
        """세션 데이터 업데이트"""
        session_data = store_get(session_id)
        if not session_data:
            return None
        for key, value in kwargs.items():
            if hasattr(session_data, key):
                setattr(session_data, key, value)
        session_data.last_updated = datetime.now()
        store_set(session_id, session_data, SESSION_TTL_SEC)
        return session_data

    def transition_state(self, session_id: str, new_state: CommerceState) -> bool:
        """상태 전이"""
        return self.update_session(session_id, state=new_state) is not None

    def delete_session(self, session_id: str) -> None:
        """세션 삭제"""
        store_delete(session_id)


state_machine = CommerceStateMachine()
