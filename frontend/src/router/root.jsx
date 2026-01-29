import { Suspense, lazy } from "react";
import { createBrowserRouter } from "react-router-dom";
import shopRouter from "./shopRouter";
import routineRouter from "./routineRouter";
import recordRouter from "./recordRouter";
import mealRouter from "./mealRouter";
import rankingRouter from "./rankingRouter";
import profileRouter from "./profileRouter";
import LoadingModal from "../components/common/LoadingModal";
import memberRouter from "./memberRouter";
import adminRouter from "./adminRouter";

const Loading = <LoadingModal isOpen={true} message="로딩 중입니다" />;

const Main = lazy(() => import("../pages/MainPage"));
const ShopIndex = lazy(() => import("../pages/shop/ShopIndex"));
const RoutineIndex = lazy(() => import("../pages/routine/RoutineIndex"));
const RecordIndex = lazy(() => import("../pages/record/RecordIndex"));
const MealIndex = lazy(() => import("../pages/meal/MealIndex"));
const RankingIndex = lazy(() => import("../pages/ranking/RankingIndex"));
const ProfileIndex = lazy(() => import("../pages/profile/ProfileIndex"));

const AdminIndex = lazy(() => import("../pages/admin/MainPage"));

const root = createBrowserRouter([
  {
    path: "/",
    element: (
      <Suspense fallback={Loading}>
        <Main />
      </Suspense>
    ),
  },
  {
    path: "/routine",
    element: (
      <Suspense fallback={Loading}>
        <RoutineIndex />
      </Suspense>
    ),
    children: routineRouter(),
  },
  {
    path: "/record",
    element: (
      <Suspense fallback={Loading}>
        <RecordIndex />
      </Suspense>
    ),
    children: recordRouter(),
  },
  {
    path: "/meal",
    element: (
      <Suspense fallback={Loading}>
        <MealIndex />
      </Suspense>
    ),
    children: mealRouter(),
  },
  {
    path: "/shop",
    element: (
      <Suspense fallback={Loading}>
        <ShopIndex />
      </Suspense>
    ),
    children: shopRouter(),
  },
  {
    path: "/ranking",
    element: (
      <Suspense fallback={Loading}>
        <RankingIndex />
      </Suspense>
    ),
    children: rankingRouter(),
  },
  {
    path: "/profile",
    element: (
      <Suspense fallback={Loading}>
        <ProfileIndex />
      </Suspense>
    ),
    children: profileRouter(),
  },
  {
    path: "member",
    children: memberRouter(),
  },
  {
    path: "admin",
    element: (
      <Suspense fallback={Loading}>
        <AdminIndex />
      </Suspense>
    ),
    children: adminRouter(),
  },
]);

export default root;
