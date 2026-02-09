import { Suspense, lazy } from "react";
import { Navigate } from "react-router-dom";

const Loading = <div>Loading...</div>;
const OrderListPage = lazy(() => import("../pages/admin/OrderListPage"));
const OrderDetailPage = lazy(() => import("../pages/admin/OrderDetailPage"));

const adminRouter = () => {
  return [
    {
      path: "",
      element: <Navigate to="orders" replace />,
    },
    {
      path: "orders",
      element: (
        <Suspense fallback={Loading}>
          <OrderListPage />
        </Suspense>
      ),
    },
    {
      path: "orders/:orderNo",
      element: (
        <Suspense fallback={Loading}>
          <OrderDetailPage />
        </Suspense>
      ),
    },
  ];
};

export default adminRouter;
