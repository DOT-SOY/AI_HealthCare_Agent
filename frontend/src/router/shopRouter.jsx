import { Suspense, lazy } from "react";
import { Navigate } from "react-router-dom";
import LoadingModal from "../components/common/LoadingModal";

const Loading = <LoadingModal isOpen={true} message="로딩 중입니다" />;

// 각 페이지 컴포넌트들을 lazy 로딩으로 불러옵니다.
const ProductList = lazy(() => import("../pages/shop/ListPage")); // 상품 목록 페이지
const ProductDetail = lazy(() => import("../pages/shop/DetailPage")); // 상품 상세 페이지
const ProductCreate = lazy(() => import("../pages/shop/admin/CreatePage")); // 관리자 상품 등록
const ProductEdit = lazy(() => import("../pages/shop/admin/EditPage")); // 관리자 상품 수정

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
      path: "detail/:id",
      element: (
        <Suspense fallback={Loading}>
          <ProductDetail />
        </Suspense>
      ),
    },
    {
      path: "admin/create",
      element: (
        <Suspense fallback={Loading}>
          <ProductCreate />
        </Suspense>
      ),
    },
    {
      path: "admin/edit/:id",
      element: (
        <Suspense fallback={Loading}>
          <ProductEdit />
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
