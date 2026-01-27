import { useEffect, useRef } from "react";
import { useSelector } from "react-redux";

/**
 * 로그인 상태 변화 시 알림 표시
 * - false -> true: "로그인 되었습니다."
 * - true -> false: "로그아웃 되었습니다."
 */
const AuthAlert = () => {
  const loginState = useSelector((state) => state.loginSlice);
  const isLogin = !!loginState?.email;
  const prevIsLoginRef = useRef(isLogin);

  useEffect(() => {
    const prev = prevIsLoginRef.current;
    if (prev === false && isLogin === true) {
      window.alert("로그인 되었습니다.");
    }
    if (prev === true && isLogin === false) {
      window.alert("로그아웃 되었습니다.");
    }
    prevIsLoginRef.current = isLogin;
  }, [isLogin]);

  return null;
};

export default AuthAlert;
