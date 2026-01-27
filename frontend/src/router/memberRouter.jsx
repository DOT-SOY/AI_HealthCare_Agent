import { Suspense, lazy } from "react";
import LoadingModal from "../components/common/LoadingModal";

const Loading = <LoadingModal isOpen={true} message="로딩 중입니다" />;
const Login = lazy(() => import("../pages/member/LoginPage"));
const KakaoRedirect = lazy(() => import("../pages/member/KakaoRedirectPage"));
const MemberModify = lazy(() => import("../pages/member/ModifyPage"));
const Join = lazy(() => import("../pages/member/JoinPage")); // Lazy load 추가

const memberRouter = () => {
  return [
    {
      path: "login",
      element: (
        <Suspense fallback={Loading}>
          <Login />
        </Suspense>
      ),
    },
    {
      path: "kakao",
      element: (
        <Suspense fallback={Loading}>
          <KakaoRedirect />
        </Suspense>
      ),
    },
    {
      path: "modify",
      element: (
        <Suspense fallback={Loading}>
          <MemberModify />
        </Suspense>
      ),
    },
    {
      path: "join",
      element: (
        <Suspense fallback={Loading}>
          <Join />
        </Suspense>
      ),
    },
  ];
};

export default memberRouter;