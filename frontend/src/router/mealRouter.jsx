import { Suspense, lazy } from "react";
import { Navigate } from "react-router-dom";
import LoadingModal from "../components/common/LoadingModal";

const Loading = <LoadingModal isOpen={true} message="로딩 중입니다" />;

const MealDashboard = lazy(() => import("../pages/meal/MealDashboard"));
const MealList = lazy(() => import("../pages/meal/ListPage"));

const mealRouter = () => {
  return [
    {
      path: "dashboard",
      element: (
        <Suspense fallback={Loading}>
          <MealDashboard />
        </Suspense>
      ),
    },
    {
      path: "list",
      element: (
        <Suspense fallback={Loading}>
          <MealList />
        </Suspense>
      ),
    },
    {
      path: "",
      element: <Navigate replace to="dashboard" />,
    },
  ];
};

export default mealRouter;
