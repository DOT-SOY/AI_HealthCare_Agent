import { Suspense, lazy } from "react";
import { Navigate } from "react-router-dom";

const Loading = <div>Loading...</div>;
// 나중에 실제 관리자 페이지를 만들면 주석을 풀고 연결하세요
// const AdminPage = lazy(() => import("../pages/admin/IndexPage"));

const adminRouter = () => {
  return [
    {
      path: "admin",
      element: <Suspense fallback={Loading}><div>관리자 페이지 준비중</div></Suspense>,
      children: [
        {
            path: "",
            element: <Navigate to="dashboard" replace />
        }
      ]
    }
  ];
};

export default adminRouter;