import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';

export function useWebSocket() {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const subscriptionRef = useRef(null);
  const callbackRef = useRef(null);

  useEffect(() => {
    // WebSocket 연결 (회고 알림을 받기 위해 항상 연결)
    if (!clientRef.current) {
      try {
        const client = new Client({
          brokerURL: 'ws://localhost:8080/ws',
          reconnectDelay: 5000,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
          connectHeaders: {},
          debug: function (str) {
            // 개발 환경에서만 디버그 로그 출력
            if (import.meta.env.DEV) {
              console.log('STOMP:', str);
            }
          },
          onConnect: (frame) => {
            setConnected(true);
            console.log('WebSocket 연결됨');
            
            // 회고 알림 구독
            if (clientRef.current && clientRef.current.connected) {
              subscriptionRef.current = clientRef.current.subscribe('/topic/workout/review', (message) => {
                try {
                  const data = JSON.parse(message.body);
                  console.log('회고 알림 수신:', data);
                  // 콜백이 등록되어 있으면 호출
                  if (callbackRef.current) {
                    callbackRef.current(data);
                  }
                } catch (error) {
                  console.error('WebSocket 메시지 파싱 오류:', error);
                }
              });
              console.log('회고 구독 완료: /topic/workout/review');
            }
          },
          onDisconnect: () => {
            setConnected(false);
            console.log('WebSocket 연결 끊김');
          },
          onStompError: (frame) => {
            // STOMP 오류는 개발 환경에서만 로그 출력
            if (import.meta.env.DEV) {
              console.error('STOMP 오류:', frame);
            }
          },
          onWebSocketError: (event) => {
            // 백엔드 서버가 실행되지 않았을 때 발생하는 오류는 조용히 처리
            // (연결 재시도가 자동으로 이루어지므로)
            if (import.meta.env.DEV) {
              console.warn('WebSocket 연결 시도 중... (백엔드 서버가 실행 중이지 않을 수 있습니다)');
            }
          },
        });
        clientRef.current = client;
        client.activate();
      } catch (error) {
        // 초기화 오류는 개발 환경에서만 로그 출력
        if (import.meta.env.DEV) {
          console.error('WebSocket 클라이언트 초기화 실패:', error);
        }
      }
    }
    
    return () => {
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
    };
  }, []);

  const subscribeToReview = (callback) => {
    // 콜백 저장
    callbackRef.current = callback;
    
    // 이미 구독되어 있으면 콜백만 저장하고 반환
    if (subscriptionRef.current) {
      return subscriptionRef.current;
    }
    
    // 연결이 완료되면 구독
    if (clientRef.current && clientRef.current.connected) {
      subscriptionRef.current = clientRef.current.subscribe('/topic/workout/review', (message) => {
        try {
          const data = JSON.parse(message.body);
          console.log('회고 알림 수신:', data);
          if (callbackRef.current) {
            callbackRef.current(data);
          }
        } catch (error) {
          console.error('WebSocket 메시지 파싱 오류:', error);
        }
      });
      return subscriptionRef.current;
    }
    
    return null;
  };

  const sendMessage = (destination, body) => {
    if (clientRef.current && clientRef.current.connected) {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  };

  return {
    connected,
    subscribeToReview,
    sendMessage,
  };
}

