import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { CookiesProvider } from "react-cookie";
import "./index.css";
import App from "./App.jsx";
import { store } from "./store.jsx";
import { ThemeProvider } from "./contexts/ThemeContext";

createRoot(document.getElementById("root")).render(
    <Provider store={store}>
      <ThemeProvider>
        <App />
      </ThemeProvider>
    </Provider>
);