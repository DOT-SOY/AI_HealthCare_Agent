import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { CookiesProvider } from "react-cookie";
import "./index.css";
import App from "./App.jsx";
import { store } from "./store.jsx";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <CookiesProvider>
      <Provider store={store}>
        <App />
      </Provider>
    </CookiesProvider>
  </StrictMode>
);