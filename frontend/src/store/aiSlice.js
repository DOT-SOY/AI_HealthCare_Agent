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
  setLastResponse,
  toggleChat,
  setChatOpen,
  clearMessages,
  setLoading,
  incrementNotification,
  clearNotification,
} = aiSlice.actions;

export default aiSlice.reducer;


