import { Suspense, lazy } from "react";
import { Navigate } from "react-router-dom";
import LoadingModal from "../components/common/LoadingModal";

const Loading = <LoadingModal isOpen={true} message="로딩 중입니다" />;

const MealList = lazy(() => import("../pages/meal/ListPage"));

const mealRouter = () => {
  return [
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
      element: <Navigate replace to="list" />,
    },
  ];
};

export default mealRouter;
