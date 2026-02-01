"""
Commerce 대화 상태머신
"""
import time
import threading
from typing import Dict, Any, Optional
from enum import Enum
from dataclasses import dataclass, field
from datetime import datetime, timedelta


class CommerceState(str, Enum):
    """상태 정의"""
    RECOMMEND = "RECOMMEND"
    CONFIRM_PRODUCT = "CONFIRM_PRODUCT"
    ADD_TO_CART = "ADD_TO_CART"
    CONFIRM_ADDRESS = "CONFIRM_ADDRESS"
    PAYMENT_READY = "PAYMENT_READY"


@dataclass
class SessionData:
    """세션 데이터"""
    state: CommerceState
    recommendation_condition: Optional[Dict[str, Any]] = None
    recommended_products: list = field(default_factory=list)
    selected_product_id: Optional[int] = None
    selected_variant_id: Optional[int] = None
    cart_id: Optional[str] = None
    order_no: Optional[str] = None
    address_id: Optional[int] = None
    last_updated: datetime = field(default_factory=datetime.now)


class CommerceStateMachine:
    """
    Commerce 대화 상태머신
    메모리 기반, 타임아웃(30분), 동시성 락 포함
    """
    
    def __init__(self, timeout_minutes: int = 30):
        self.sessions: Dict[str, SessionData] = {}
        self.locks: Dict[str, threading.Lock] = {}
        self.timeout_minutes = timeout_minutes
        self.global_lock = threading.Lock()
    
    def _get_lock(self, session_id: str) -> threading.Lock:
        """세션별 락 가져오기 (없으면 생성)"""
        with self.global_lock:
            if session_id not in self.locks:
                self.locks[session_id] = threading.Lock()
            return self.locks[session_id]
    
    def _cleanup_expired_sessions(self):
        """만료된 세션 정리"""
        now = datetime.now()
        expired_sessions = []
        
        with self.global_lock:
            for session_id, session_data in self.sessions.items():
                if (now - session_data.last_updated) > timedelta(minutes=self.timeout_minutes):
                    expired_sessions.append(session_id)
            
            for session_id in expired_sessions:
                del self.sessions[session_id]
                if session_id in self.locks:
                    del self.locks[session_id]
    
    def get_session(self, session_id: str) -> Optional[SessionData]:
        """세션 데이터 조회"""
        self._cleanup_expired_sessions()
        
        with self.global_lock:
            return self.sessions.get(session_id)
    
    def create_session(self, session_id: str) -> SessionData:
        """새 세션 생성"""
        lock = self._get_lock(session_id)
        
        with lock:
            session_data = SessionData(state=CommerceState.RECOMMEND)
            with self.global_lock:
                self.sessions[session_id] = session_data
            return session_data
    
    def update_session(self, session_id: str, **kwargs) -> Optional[SessionData]:
        """세션 데이터 업데이트"""
        self._cleanup_expired_sessions()
        
        lock = self._get_lock(session_id)
        
        with lock:
            with self.global_lock:
                session_data = self.sessions.get(session_id)
                if not session_data:
                    return None
                
                # 업데이트
                for key, value in kwargs.items():
                    if hasattr(session_data, key):
                        setattr(session_data, key, value)
                
                session_data.last_updated = datetime.now()
                return session_data
    
    def transition_state(self, session_id: str, new_state: CommerceState) -> bool:
        """상태 전이"""
        return self.update_session(session_id, state=new_state) is not None
    
    def delete_session(self, session_id: str):
        """세션 삭제"""
        lock = self._get_lock(session_id)
        
        with lock:
            with self.global_lock:
                if session_id in self.sessions:
                    del self.sessions[session_id]
                if session_id in self.locks:
                    del self.locks[session_id]


# 전역 상태머신 인스턴스
state_machine = CommerceStateMachine()

