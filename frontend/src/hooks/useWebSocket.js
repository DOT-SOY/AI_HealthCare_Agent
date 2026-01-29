import { useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getCookie } from '../util/cookieUtil';

// 모듈 레벨에서 마지막 리뷰 알림을 기억하여 중복 수신 방지
let lastReviewRoutineId = null;
let lastReviewMessage = null;
let lastReviewTime = 0;

export function useWebSocket() {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const subscriptionRef = useRef(null);
  const callbackRef = useRef(null);
  const isConnectingRef = useRef(false);

  // 공통 메시지 처리 함수
  const handleMessage = useCallback((message) => {
    try {
      const data = JSON.parse(message.body);

      // 리뷰 알림 중복 필터링 (StrictMode 등으로 인한 중복 구독 대비)
      const routineId = data?.routineId;
      const messageContent = data?.message;
      const now = Date.now();

      // 같은 routineId에 대한 알림이 짧은 시간(5초) 내에 다시 오면 무시
      if (
        routineId != null &&
        lastReviewRoutineId === routineId &&
        now - lastReviewTime < 5000
      ) {
        return;
      }

      // routineId가 없더라도, 같은 메시지가 매우 짧은 시간(1초) 내에 두 번 오면 무시
      if (
        messageContent &&
        lastReviewMessage === messageContent &&
        now - lastReviewTime < 1000
      ) {
        return;
      }

      lastReviewRoutineId = routineId ?? lastReviewRoutineId;
      lastReviewMessage = messageContent ?? lastReviewMessage;
      lastReviewTime = now;

      if (callbackRef.current) {
        callbackRef.current(data);
      }
    } catch (error) {
      console.error('WebSocket 메시지 파싱 오류:', error);
    }
  }, []);

  // 실제 구독을 수행하는 함수
  const doSubscribe = useCallback(() => {
    if (!clientRef.current || !clientRef.current.connected) {
      return;
    }

    // 이미 구독되어 있으면 기존 구독 해제 후 재구독 (중복 구독 방지)
    if (subscriptionRef.current) {
      try {
        subscriptionRef.current.unsubscribe();
      } catch (error) {
        // 구독 해제 중 오류는 무시
      }
      subscriptionRef.current = null;
    }

    subscriptionRef.current = clientRef.current.subscribe(
      '/topic/workout/review',
      handleMessage
    );
  }, [handleMessage]);

  // WebSocket 연결 함수 (필요할 때만 호출)
  const connectWebSocket = useCallback(() => {
    // 이미 연결되어 있거나 연결 중이면 무시
    if (clientRef.current?.connected || isConnectingRef.current) {
      if (import.meta.env.DEV) {
        console.log('WebSocket 이미 연결되어 있거나 연결 중입니다.');
      }
      return;
    }

    // 쿠키에서 JWT 토큰 가져오기
    const memberInfo = getCookie('member');
    const token = memberInfo?.accessToken;
    
    // 토큰이 없으면 WebSocket 연결하지 않음
    if (!token) {
      if (import.meta.env.DEV) {
        console.warn('WebSocket 연결 실패: JWT 토큰이 없습니다.');
      }
      return;
    }
    
    if (import.meta.env.DEV) {
      console.log('WebSocket 연결 시도 중... 토큰 존재:', !!token);
    }
    
    // 이미 클라이언트가 있으면 재활용
    if (clientRef.current) {
      if (!clientRef.current.connected) {
        isConnectingRef.current = true;
        clientRef.current.activate();
      }
      return;
    }
    
    // 새 클라이언트 생성 및 연결
    try {
      isConnectingRef.current = true;
      const client = new Client({
        webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
        reconnectDelay: 0, // 자동 재연결 비활성화
        heartbeatIncoming: 0, // heartbeat 비활성화 (이벤트 기반이므로 불필요)
        heartbeatOutgoing: 0, // heartbeat 비활성화
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        debug: function (str) {
          // 디버그 로그 제거 (필요시 주석 해제)
          // if (import.meta.env.DEV) {
          //   console.log('STOMP:', str);
          // }
        },
        onConnect: (frame) => {
          setConnected(true);
          isConnectingRef.current = false;

          // 콜백이 이미 설정되어 있다면, 연결 완료 시점에 한 번 더 구독 시도
          if (callbackRef.current) {
            doSubscribe();
          }
        },
        onDisconnect: () => {
          setConnected(false);
          isConnectingRef.current = false;
          // 연결 끊김 로그 제거
        },
        onStompError: (frame) => {
          isConnectingRef.current = false;
          // STOMP 오류 상세 로그
          console.error('WebSocket STOMP 오류:', {
            command: frame.command,
            headers: frame.headers,
            body: frame.body,
            message: frame.headers?.['message'] || frame.headers?.['error'] || 'Unknown STOMP error'
          });
          if (clientRef.current) {
            clientRef.current.deactivate();
            clientRef.current = null;
          }
        },
        onWebSocketError: (event) => {
          isConnectingRef.current = false;
          console.error('WebSocket 연결 실패:', {
            type: event.type,
            target: event.target,
            error: event.error || 'Unknown error'
          });
          if (clientRef.current) {
            clientRef.current.deactivate();
            clientRef.current = null;
          }
        },
      });
      clientRef.current = client;
      client.activate();
    } catch (error) {
      isConnectingRef.current = false;
      if (import.meta.env.DEV) {
        console.error('WebSocket 클라이언트 초기화 실패:', error);
      }
    }
  }, [doSubscribe]);

  const subscribeToReview = useCallback(
    (callback) => {
      // 콜백 저장
      callbackRef.current = callback;

      // WebSocket이 연결되어 있지 않으면 연결 시도만 하고 반환
      if (!clientRef.current?.connected && !isConnectingRef.current) {
        connectWebSocket();
        return null;
      }

      // 이미 연결되어 있으면 즉시 구독 수행
      doSubscribe();
      return subscriptionRef.current;
    },
    [connectWebSocket, doSubscribe]
  );

  const sendMessage = useCallback((destination, body) => {
    if (clientRef.current && clientRef.current.connected) {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  }, []);

  // 정리 함수
  const disconnect = useCallback(() => {
    if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe();
      subscriptionRef.current = null;
    }
    if (clientRef.current) {
      try {
        clientRef.current.deactivate();
        clientRef.current = null;
      } catch (error) {
        // 정리 중 오류는 무시
      }
    }
    setConnected(false);
    isConnectingRef.current = false;
  }, []);

  return {
    connected,
    connectWebSocket,
    subscribeToReview,
    sendMessage,
    disconnect,
  };
}

