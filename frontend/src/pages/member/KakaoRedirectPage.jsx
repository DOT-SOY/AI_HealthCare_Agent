import { useEffect, useRef } from "react";
import { useDispatch } from "react-redux";
import { useNavigate, useSearchParams } from "react-router-dom";
import { getAccessToken, getMemberWithAccessToken } from "../../api/kakaoApi";
import { login } from "../../slices/loginSlice";

const KakaoRedirectPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const didRunRef = useRef(false);

  useEffect(() => {
    // 개발 모드(StrictMode) 등으로 useEffect가 2번 실행되는 경우를 막기 위한 1회 실행 가드
    if (didRunRef.current) return;
    didRunRef.current = true;

    const error = searchParams.get("error");
    const code = searchParams.get("code");

    if (error) {
      alert("카카오 로그인에 실패했습니다.");
      navigate("/member/login", { replace: true });
      return;
    }

    if (!code) {
      alert("카카오 인증 코드가 없습니다.");
      navigate("/member/login", { replace: true });
      return;
    }

    (async () => {
      try {
        // 1) 프론트에서 카카오 access token 발급
        const kakaoAccessToken = await getAccessToken(code);

        // 2) 백엔드에 카카오 access token 전달 → 우리 서비스 로그인 처리(AccessToken + RefreshCookie)
        const memberInfo = await getMemberWithAccessToken(kakaoAccessToken);

        // 3) Redux 로그인 상태 저장(쿠키 member 저장 포함)
        dispatch(login(memberInfo));

        // 4) 홈으로 이동
        navigate("/", { replace: true });
      } catch (e) {
        console.error("Kakao login redirect error:", e);
        const msg =
          e?.data?.error_description ||
          e?.data?.error ||
          e?.response?.data?.message ||
          e?.response?.data?.error ||
          e?.message ||
          "카카오 로그인에 실패했습니다.";
        alert(msg);
        navigate("/member/login", { replace: true });
      }
    })();
  }, [dispatch, navigate, searchParams]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-gray-600">카카오 로그인 처리 중...</div>
    </div>
  );
};

export default KakaoRedirectPage;