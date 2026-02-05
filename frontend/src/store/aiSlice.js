import { createSlice } from '@reduxjs/toolkit';

const aiSlice = createSlice({
  name: 'ai',
  initialState: {
    messages: [],
    lastResponse: null,
    isChatOpen: false,
    loading: false,
    notificationCount: 0, // 알림 개수
  },
  reducers: {
    addMessage: (state, action) => {
      state.messages.push(action.payload);
    },
    upsertMealGenerateMessage: (state, action) => {
      const content = action.payload?.content ?? '';
      // 마지막 MEAL_GENERATE 메시지가 있으면 그걸 업데이트(퍼센트만 바뀌는 UX)
      for (let i = state.messages.length - 1; i >= 0; i -= 1) {
        const m = state.messages[i];
        if (m?.role === 'assistant' && m?.meta?.kind === 'MEAL_GENERATE') {
          state.messages[i] = { ...m, content };
          return;
        }
      }
      // 없으면 새로 추가
      state.messages.push({ role: 'assistant', content, meta: { kind: 'MEAL_GENERATE' } });
    },
    setLastResponse: (state, action) => {
      state.lastResponse = action.payload;
    },
    toggleChat: (state) => {
      const wasOpen = state.isChatOpen;
      state.isChatOpen = !state.isChatOpen;
      // 채팅창을 열 때 알림 카운트 초기화 (닫혀있었다가 열릴 때)
      if (!wasOpen && state.isChatOpen) {
        state.notificationCount = 0;
      }
    },
    setChatOpen: (state, action) => {
      state.isChatOpen = action.payload;
      // 채팅창을 열 때 알림 카운트 초기화
      if (action.payload) {
        state.notificationCount = 0;
      }
    },
    clearMessages: (state) => {
      state.messages = [];
      state.lastResponse = null;
    },
    setLoading: (state, action) => {
      state.loading = action.payload;
    },
    incrementNotification: (state) => {
      state.notificationCount += 1;
    },
    clearNotification: (state) => {
      state.notificationCount = 0;
    },
  },
});

export const {
  addMessage,
  upsertMealGenerateMessage,
  setLastResponse,
  toggleChat,
  setChatOpen,
  clearMessages,
  setLoading,
  incrementNotification,
  clearNotification,
} = aiSlice.actions;

export default aiSlice.reducer;


