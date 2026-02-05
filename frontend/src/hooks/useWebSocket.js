import { useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import { getCookie } from '../util/cookieUtil';

// 모듈 레벨에서 마지막 리뷰 알림을 기억하여 중복 수신 방지
let lastReviewRoutineId = null;
let lastReviewMessage = null;
let lastReviewTime = 0;

export function useWebSocket() {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const subscriptionRef = useRef(null);
  const mealSubscriptionRef = useRef(null);
  const mealVisionSubscriptionRef = useRef(null);
  const mealErrorSubscriptionRef = useRef(null);
  const mealReplanSubscriptionRef = useRef(null);
  const mealChangedSubscriptionRef = useRef(null);
  const callbackRef = useRef(null);
  const mealCallbackRef = useRef(null);
  const mealVisionCallbackRef = useRef(null);
  const mealErrorCallbackRef = useRef(null);
  const mealReplanCallbackRef = useRef(null);
  const mealChangedCallbackRef = useRef(null);
  const subscriptionRoutineRef = useRef(null);
  const routineUpdateCallbackRef = useRef(null);
  const isConnectingRef = useRef(false);

  const parseMemberCookie = useCallback(() => {
    const raw = getCookie('member');
    if (!raw) return null;
    try {
      return typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch (e) {
      return null;
    }
  }, []);

  const getAccessTokenFromCookie = useCallback(() => {
    const obj = parseMemberCookie();
    const token = obj?.accessToken ?? null;
    if (token) return token;
    // fallback: 일부 로그인/갱신 플로우는 localStorage에만 accessToken이 있을 수 있음
    try {
      return localStorage.getItem('accessToken');
    } catch (_) {
      return null;
    }
  }, [parseMemberCookie]);

  const getUserIdFromCookie = useCallback(() => {
    const obj = parseMemberCookie();
    const fromCookie = obj?.memberId ?? obj?.id ?? obj?.userId ?? null;
    if (fromCookie != null) return fromCookie;

    // fallback: accessToken payload에서 memberId 추출 (refresh로 쿠키만 갱신되거나, member 쿠키가 비정상일 때 대비)
    const token = getAccessTokenFromCookie();
    if (!token || typeof token !== 'string') return null;
    try {
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const payloadB64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = payloadB64 + '='.repeat((4 - (payloadB64.length % 4)) % 4);
      const json = decodeURIComponent(
        Array.prototype.map.call(atob(padded), (c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')
      );
      const payload = JSON.parse(json);
      return payload?.memberId ?? payload?.id ?? payload?.userId ?? null;
    } catch (_) {
      return null;
    }
  }, [parseMemberCookie, getAccessTokenFromCookie]);

  // 루틴 생성/수정 알림 처리
  const handleRoutineMessage = useCallback((message) => {
    try {
      const data = JSON.parse(message.body);
      if (routineUpdateCallbackRef.current) {
        routineUpdateCallbackRef.current(data);
      }
    } catch (error) {
      console.error('루틴 WebSocket 메시지 파싱 오류:', error);
    }
  }, []);

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

  // 실제 구독을 수행하는 함수 (리뷰 알림)
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

  // 식단 생성 완료 구독 함수
  const doSubscribeMeal = useCallback((userId) => {
    if (!clientRef.current || !clientRef.current.connected || !userId) {
      return;
    }

    // 이미 구독되어 있으면 기존 구독 해제 후 재구독
    if (mealSubscriptionRef.current) {
      try {
        mealSubscriptionRef.current.unsubscribe();
      } catch (error) {
        // 구독 해제 중 오류는 무시
      }
      mealSubscriptionRef.current = null;
    }

    mealSubscriptionRef.current = clientRef.current.subscribe(
      `/topic/meal/generate/${userId}`,
      (message) => {
        try {
          const data = message.body; // 문자열 메시지
          if (mealCallbackRef.current) {
            mealCallbackRef.current({ message: data });
          }
        } catch (error) {
          console.error('식단 생성 알림 파싱 오류:', error);
        }
      }
    );
  }, []);

  const doSubscribeMealVision = useCallback((userId) => {
    if (!clientRef.current || !clientRef.current.connected || !userId) return;

    if (mealVisionSubscriptionRef.current) {
      try {
        mealVisionSubscriptionRef.current.unsubscribe();
      } catch (_) {}
      mealVisionSubscriptionRef.current = null;
    }

    mealVisionSubscriptionRef.current = clientRef.current.subscribe(
      `/topic/meal/vision/${userId}`,
      (message) => {
        try {
          const data = JSON.parse(message.body);
          if (mealVisionCallbackRef.current) {
            mealVisionCallbackRef.current(data);
          }
        } catch (error) {
          console.error('식단 Vision 알림 파싱 오류:', error);
        }
      }
    );
  }, []);

  const doSubscribeMealError = useCallback((userId) => {
    if (!clientRef.current || !clientRef.current.connected || !userId) return;

    if (mealErrorSubscriptionRef.current) {
      try {
        mealErrorSubscriptionRef.current.unsubscribe();
      } catch (_) {}
      mealErrorSubscriptionRef.current = null;
    }

    mealErrorSubscriptionRef.current = clientRef.current.subscribe(
      `/topic/meal/error/${userId}`,
      (message) => {
        try {
          const data = message.body; // string
          if (mealErrorCallbackRef.current) {
            mealErrorCallbackRef.current({ message: data });
          }
        } catch (error) {
          console.error('식단 Error 알림 파싱 오류:', error);
        }
      }
    );
  }, []);

  const doSubscribeMealReplan = useCallback((userId) => {
    if (!clientRef.current || !clientRef.current.connected || !userId) return;

    if (mealReplanSubscriptionRef.current) {
      try {
        mealReplanSubscriptionRef.current.unsubscribe();
      } catch (_) {}
      mealReplanSubscriptionRef.current = null;
    }

    mealReplanSubscriptionRef.current = clientRef.current.subscribe(
      `/topic/meal/replan/${userId}`,
      (message) => {
        try {
          const data = message.body; // string
          if (mealReplanCallbackRef.current) {
            mealReplanCallbackRef.current({ message: data });
          }
        } catch (error) {
          console.error('식단 Replan 알림 파싱 오류:', error);
        }
      }
    );
  }, []);
    subscriptionRef.current = clientRef.current.subscribe(
      '/topic/workout/review',
      handleMessage
    );
  }, [handleMessage]);

  // 루틴 생성/수정 알림 구독 (요일 맞바꾸기 등 후 자동 새로고침용)
  const doSubscribeRoutine = useCallback(() => {
    if (!clientRef.current || !clientRef.current.connected || !routineUpdateCallbackRef.current) {
      return;
    }
    if (subscriptionRoutineRef.current) {
      try {
        subscriptionRoutineRef.current.unsubscribe();
      } catch (e) {}
      subscriptionRoutineRef.current = null;
    }
    subscriptionRoutineRef.current = clientRef.current.subscribe(
      '/topic/routine/generate',
      handleRoutineMessage
    );
  }, [handleRoutineMessage]);

  // WebSocket 연결 함수 (필요할 때만 호출)
  const connectWebSocket = useCallback(() => {
    // 이미 연결되어 있거나 연결 중이면 무시
    if (clientRef.current?.connected || isConnectingRef.current) {
      if (import.meta.env.DEV) {
        console.log('WebSocket 이미 연결되어 있거나 연결 중입니다.');
      }
      return;
    }

    // 쿠키에서 JWT 토큰 가져오기 (member 쿠키는 JSON string일 수 있음)
    const token = getAccessTokenFromCookie();
    
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

      // API 서버 호스트와 동일한 호스트로 WS 연결 (하드코딩 localhost 제거)
      const rawHost = import.meta.env.VITE_API_SERVER_HOST || window.location.origin;
      const host = String(rawHost || '').replace(/\/+$/, '').replace(/\/api\/?$/, '');
      const brokerURL = host.startsWith('ws://') || host.startsWith('wss://')
        ? `${host}/ws`
        : host.startsWith('https://')
          ? `wss://${host.slice('https://'.length)}/ws`
          : host.startsWith('http://')
            ? `ws://${host.slice('http://'.length)}/ws`
            : `${window.location.protocol === 'https:' ? 'wss://' : 'ws://'}${host}/ws`;

      const client = new Client({
        brokerURL,
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
          // 식단 생성 구독도 재시도
          if (mealCallbackRef.current) {
            const userId = getUserIdFromCookie();
            if (userId) {
              doSubscribeMeal(userId);
            }
          }
          // 식단 Vision/Error 구독 재시도
          const userId = getUserIdFromCookie();
          if (userId) {
            if (mealVisionCallbackRef.current) doSubscribeMealVision(userId);
            if (mealErrorCallbackRef.current) doSubscribeMealError(userId);
            if (mealReplanCallbackRef.current) doSubscribeMealReplan(userId);
            if (mealChangedCallbackRef.current) doSubscribeMealChanged(userId);
          }
          // 콜백이 이미 설정되어 있다면, 연결 완료 시점에 구독
          if (callbackRef.current) {
            doSubscribe();
          }
          if (routineUpdateCallbackRef.current) {
            doSubscribeRoutine();
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
  }, [doSubscribe, getAccessTokenFromCookie, getUserIdFromCookie, doSubscribeMeal, doSubscribeMealVision, doSubscribeMealError,doSubscribeRoutine]);


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

  const subscribeToMealGenerate = useCallback(
    (callback) => {
      // 콜백 저장
      mealCallbackRef.current = callback;

      // userId 가져오기
      const userId = getUserIdFromCookie();

      if (!userId) {
        console.warn('식단 생성 알림 구독 실패: userId를 찾을 수 없습니다.');
        return null;
      }

      // WebSocket이 연결되어 있지 않으면 연결 시도만 하고 반환
      if (!clientRef.current?.connected && !isConnectingRef.current) {
        connectWebSocket();
        return null;
      }

      // 이미 연결되어 있으면 즉시 구독 수행
      doSubscribeMeal(userId);
      return mealSubscriptionRef.current;
    },
    [connectWebSocket, doSubscribeMeal, getUserIdFromCookie]
  );

  const subscribeToMealVision = useCallback(
    (callback) => {
      mealVisionCallbackRef.current = callback;
      const userId = getUserIdFromCookie();
      if (!userId) {
        console.warn('식단 Vision 알림 구독 실패: userId를 찾을 수 없습니다.');
        return null;
      }
      if (!clientRef.current?.connected && !isConnectingRef.current) {
        connectWebSocket();
        return null;
      }
      doSubscribeMealVision(userId);
      return mealVisionSubscriptionRef.current;
    },
    [connectWebSocket, doSubscribeMealVision, getUserIdFromCookie]
  );

  const subscribeToMealError = useCallback(
    (callback) => {
      mealErrorCallbackRef.current = callback;
      const userId = getUserIdFromCookie();
      if (!userId) {
        console.warn('식단 Error 알림 구독 실패: userId를 찾을 수 없습니다.');
        return null;
      }
      if (!clientRef.current?.connected && !isConnectingRef.current) {
        connectWebSocket();
        return null;
      }
      doSubscribeMealError(userId);
      return mealErrorSubscriptionRef.current;
      // WebSocket이 연결되어 있지 않으면 연결 시도만 하고 반환
      if (!clientRef.current?.connected && !isConnectingRef.current) {
        connectWebSocket();
        return null;
      }

      // 이미 연결되어 있으면 즉시 구독 수행
      doSubscribe();
      return subscriptionRef.current;
    },
    [connectWebSocket, doSubscribeMealError, getUserIdFromCookie]
  );

  const subscribeToMealReplan = useCallback(
    (callback) => {
      mealReplanCallbackRef.current = callback;
      const userId = getUserIdFromCookie();
      if (!userId) {
        console.warn('식단 Replan 알림 구독 실패: userId를 찾을 수 없습니다.');
        return null;
      }
      if (!clientRef.current?.connected && !isConnectingRef.current) {
        connectWebSocket();
        return null;
      }
      doSubscribeMealReplan(userId);
      return mealReplanSubscriptionRef.current;
    },
    [connectWebSocket, doSubscribeMealReplan, getUserIdFromCookie]
  );

  const doSubscribeMealChanged = useCallback((userId) => {
    if (!clientRef.current || !clientRef.current.connected || !userId) return;

    if (mealChangedSubscriptionRef.current) {
      try {
        mealChangedSubscriptionRef.current.unsubscribe();
      } catch (_) {}
      mealChangedSubscriptionRef.current = null;
    }

    mealChangedSubscriptionRef.current = clientRef.current.subscribe(
      `/topic/meal/changed/${userId}`,
      (message) => {
        try {
          if (mealChangedCallbackRef.current) {
            mealChangedCallbackRef.current({ message: message.body });
          }
        } catch (error) {
          console.error('식단 변경 알림 파싱 오류:', error);
        }
      }
    );
  }, []);

  const subscribeToMealChanged = useCallback(
    (callback) => {
      mealChangedCallbackRef.current = callback;
      const userId = getUserIdFromCookie();
      if (!userId) return null;
      if (!clientRef.current?.connected && !isConnectingRef.current) {
        connectWebSocket();
        return null;
      }
      doSubscribeMealChanged(userId);
      return mealChangedSubscriptionRef.current;
    },
    [connectWebSocket, doSubscribeMealChanged, getUserIdFromCookie]
  );

  const sendMessage = useCallback((destination, body) => {
    if (clientRef.current && clientRef.current.connected) {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  }, []);

  const subscribeToRoutineUpdate = useCallback(
    (callback) => {
      routineUpdateCallbackRef.current = callback;
      if (clientRef.current?.connected) {
        doSubscribeRoutine();
      } else if (!isConnectingRef.current) {
        connectWebSocket();
      }
    },
    [connectWebSocket, doSubscribeRoutine]
  );

  // 정리 함수
  const disconnect = useCallback(() => {
    if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe();
      subscriptionRef.current = null;
    }
    if (mealSubscriptionRef.current) {
      mealSubscriptionRef.current.unsubscribe();
      mealSubscriptionRef.current = null;
    }
    if (mealVisionSubscriptionRef.current) {
      mealVisionSubscriptionRef.current.unsubscribe();
      mealVisionSubscriptionRef.current = null;
    }
    if (mealErrorSubscriptionRef.current) {
      mealErrorSubscriptionRef.current.unsubscribe();
      mealErrorSubscriptionRef.current = null;
    }
    if (mealReplanSubscriptionRef.current) {
      mealReplanSubscriptionRef.current.unsubscribe();
      mealReplanSubscriptionRef.current = null;
    }
    if (mealChangedSubscriptionRef.current) {
      mealChangedSubscriptionRef.current.unsubscribe();
      mealChangedSubscriptionRef.current = null;
    }
    if (subscriptionRoutineRef.current) {
      try {
        subscriptionRoutineRef.current.unsubscribe();
      } catch (e) {}
      subscriptionRoutineRef.current = null;
    }
    routineUpdateCallbackRef.current = null;
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
    subscribeToRoutineUpdate,
    subscribeToMealGenerate,
    subscribeToMealVision,
    subscribeToMealError,
    subscribeToMealReplan,
    subscribeToMealChanged,
    sendMessage,
    disconnect,
  };
}

