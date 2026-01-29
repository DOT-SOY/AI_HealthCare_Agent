import { configureStore } from "@reduxjs/toolkit";
import loginReducer from "./slices/loginSlice";
import routinesReducer from './store/routinesSlice';
import aiReducer from './store/aiSlice';

export const store = configureStore({
  reducer: {
    loginSlice: loginReducer,
    routines: routinesReducer,
    ai: aiReducer,
  },
});