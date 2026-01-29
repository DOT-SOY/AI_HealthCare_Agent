import { useDispatch, useSelector } from "react-redux";
import { createSearchParams, Navigate, useNavigate } from "react-router-dom";
import { loginPostAsync, logout } from "../slices/loginSlice";

const useCustomLogin = () => {
  const navigate = useNavigate();

  const dispatch = useDispatch();

  const loginState = useSelector((state) => state.loginSlice); //-------로그인 상태 (Redux loginSlice에서 로그인 상태를 가져옴)

  const isLogin = loginState.email ? true : false; //----------로그인 여부

  const doLogin = async (loginParam) => {
    //----------로그인 함수

    const action = await dispatch(loginPostAsync(loginParam)); // 비동기 로그인 처리 (일반 로그인)

    // fulfilled 또는 rejected 모두 action.payload에 데이터가 있음
    // rejected인 경우 action.type에 "rejected"가 포함됨
    if (action.type.includes("rejected")) {
      // rejected인 경우 예외를 던져서 catch로 처리되도록 함
      throw action.payload;
    }

    return action.payload;
  };

  const doLogout = async () => {
    //---------------로그아웃 함수 (비동기 - logoutPostAsync 호출)
    await dispatch(logout());
  };

  const moveToPath = (path) => {
    //----------------페이지 이동
    navigate({ pathname: path }, { replace: true });
  };

  const moveToLogin = () => {
    //----------------------로그인 페이지로 이동
    navigate({ pathname: "/member/login" }, { replace: true });
  };

  const moveToLoginReturn = () => {
    //----------------------로그인 페이지로 이동 컴포넌트
    return <Navigate replace to="/member/login" />;
  };

  const exceptionHandle = (ex) => {
    console.log("Exception----------------------");

    console.log(ex);

    const errorMsg = ex.response.data.error;

    const errorStr = createSearchParams({ error: errorMsg }).toString();

    if (errorMsg === "REQUIRE_LOGIN") {
      alert(" 로그인이 필요한 서비스입니다.");
      navigate({ pathname: "/member/login", search: errorStr });
      return;
    }

    if (ex.response.data.error === "ERROR_ACCESSDENIED") {
      alert("해당 메뉴를 사용할수 있는 권한이 없습니다.");
      navigate({ pathname: "/member/login", search: errorStr });
      return;
    }
  };
  return {
    exceptionHandle,
    loginState,
    isLogin,
    doLogin,
    doLogout,
    moveToPath,
    moveToLogin,
    moveToLoginReturn,
  };
};

export default useCustomLogin;