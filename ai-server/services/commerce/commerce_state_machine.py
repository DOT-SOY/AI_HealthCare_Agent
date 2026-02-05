"""Commerce 상태머신 (Redis, TTL 30분)."""
from typing import Optional
from datetime import datetime

from .commerce_types import CommerceState, SessionData
from .commerce_session_store import get as store_get, set as store_set, delete as store_delete

SESSION_TTL_SEC = 1800


class CommerceStateMachine:
    def get_session(self, session_id: str) -> Optional[SessionData]:
        return store_get(session_id)

    def create_session(self, session_id: str) -> SessionData:
        session_data = SessionData(state=CommerceState.RECOMMEND)
        store_set(session_id, session_data, SESSION_TTL_SEC)
        return session_data

    def update_session(self, session_id: str, **kwargs) -> Optional[SessionData]:
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
        return self.update_session(session_id, state=new_state) is not None

    def delete_session(self, session_id: str) -> None:
        store_delete(session_id)


state_machine = CommerceStateMachine()
