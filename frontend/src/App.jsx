import { useEffect } from "react";
import { useSelector } from "react-redux";
import { RouterProvider } from "react-router-dom";
import "./App.css";
import root from "./router/root";
import AuthAlert from "./components/common/AuthAlert";
import { startAutoRefresh, stopAutoRefresh } from "./util/jwtUtil";

function App() {
  const isLogin = useSelector((state) => !!state.loginSlice?.email);

  useEffect(() => {
    if (isLogin) {
      startAutoRefresh();
      return () => stopAutoRefresh();
    }
  }, [isLogin]);

  return (
    <>
      <AuthAlert />
      <RouterProvider router={root} />
    </>
  );
}

export default App;