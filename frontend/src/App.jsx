import { RouterProvider } from "react-router-dom";
import "./App.css";
import root from "./router/root";
import AuthAlert from "./components/common/AuthAlert";

function App() {
  return (
    <>
      <AuthAlert />
      <RouterProvider router={root} />
    </>
  );
}

export default App;