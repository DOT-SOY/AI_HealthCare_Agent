import { configureStore } from "@reduxjs/toolkit";
import loginReducer from "./slices/loginSlice";

export const store = configureStore({
  reducer: {
    loginSlice: loginReducer,
  },
});