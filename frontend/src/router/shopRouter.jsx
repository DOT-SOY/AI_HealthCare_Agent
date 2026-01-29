import { Suspense, lazy } from "react";
import { Navigate } from "react-router-dom";
import LoadingModal from "../components/common/LoadingModal";

const Loading = <LoadingModal isOpen={true} message="로딩 중입니다" />;

// 각 페이지 컴포넌트들을 lazy 로딩으로 불러옵니다.
const ProductList = lazy(() => import("../pages/shop/ListPage")); // 상품 목록 페이지
const ProductDetail = lazy(() => import("../pages/shop/DetailPage")); // 상품 상세 페이지
const ProductCreate = lazy(() => import("../pages/shop/admin/CreatePage")); // 관리자 상품 등록
const ProductEdit = lazy(() => import("../pages/shop/admin/EditPage")); // 관리자 상품 수정
const CheckoutPage = lazy(() => import("../pages/shop/CheckoutPage")); // 결제하기(주문/결제 준비)
const PaymentSuccessPage = lazy(() => import("../pages/shop/PaymentSuccessPage")); // 결제 성공
const PaymentFailPage = lazy(() => import("../pages/shop/PaymentFailPage")); // 결제 실패
const OrderDetailPage = lazy(() => import("../pages/shop/OrderDetailPage")); // 주문 상세

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
      path: "checkout",
      element: (
        <Suspense fallback={Loading}>
          <CheckoutPage />
        </Suspense>
      ),
    },
    {
      path: "payment/success",
      element: (
        <Suspense fallback={Loading}>
          <PaymentSuccessPage />
        </Suspense>
      ),
    },
    {
      path: "payment/fail",
      element: (
        <Suspense fallback={Loading}>
          <PaymentFailPage />
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
    {
      path: "",
      element: <Navigate replace to="list" />,
    },
  ];
};

export default shopRouter;
