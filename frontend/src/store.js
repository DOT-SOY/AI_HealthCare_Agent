import { configureStore } from '@reduxjs/toolkit';
import routinesReducer from './store/routinesSlice';
import aiReducer from './store/aiSlice';

export const store = configureStore({
  reducer: {
    routines: routinesReducer,
    ai: aiReducer,
  },
});


