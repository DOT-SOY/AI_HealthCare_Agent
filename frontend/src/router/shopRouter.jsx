import { Suspense, lazy } from "react";
import { Navigate } from "react-router-dom";
import LoadingModal from "../components/common/LoadingModal";

const Loading = <LoadingModal isOpen={true} message="로딩 중입니다" />;

// 각 페이지 컴포넌트들을 lazy 로딩으로 불러옵니다.
// path: "list"
const ProductList = lazy(() => import("../pages/shop/ListPage")); // 상품 목록 페이지

const shopRouter = () => {
  return [
    {
      path: "list",
      element: (
        <Suspense fallback={Loading}>
          <ProductList />
        </Suspense>
      ),
    },
    {
      path: "",
      element: <Navigate replace to="list" />,
    },
  ];
};

export default shopRouter;
