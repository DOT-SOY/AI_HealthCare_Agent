import { useState } from "react";
// import { useDispatch } from "react-redux"; // 안 쓰므로 제거
// import { login } from "../../slices/loginSlice"; // 안 쓰므로 제거
import useCustomLogin from "../../hooks/useCustomLogin";
import KakaoLoginComponent from "./KakaoLoginComponent";
import { mergeCart } from "../../services/cartApi";
// import LoadingModal from "../common/LoadingModal"; // 안 쓰므로 제거

const initState = { email: "", pw: "" };

const LoginComponent = () => {
  const [loginParam, setLoginParam] = useState({ ...initState });
  const { doLogin, moveToPath } = useCustomLogin();

  const handleChange = (e) => {
    setLoginParam({ ...loginParam, [e.target.name]: e.target.value });
  };

  const handleError = (errorData) => {
    let error, message, remainingAttempts, remainingMinutes;

    if (typeof errorData === "object" && errorData !== null) {
      error = errorData.error;
      message = errorData.message;
      remainingAttempts = errorData.remainingAttempts;
      remainingMinutes = errorData.remainingMinutes;
    } else {
      error = errorData;
    }

    if (error === "ACCOUNT_LOCKED") {
      if (remainingMinutes !== undefined && remainingMinutes !== null && remainingMinutes >= 0) {
        alert(`로그인이 잠겨 있습니다.\n남은 시간: ${remainingMinutes}분`);
        return;
      } else if (message) {
        if (message.includes("남은 시간:")) {
          alert(message);
          return;
        } else {
          const timeMatch = message.match(/남은 시간:\s*(\d+)/);
          if (timeMatch && timeMatch[1]) {
            const extractedMinutes = parseInt(timeMatch[1]);
            alert(`로그인이 잠겨 있습니다.\n남은 시간: ${extractedMinutes}분`);
            return;
          }
        }
        alert(message);
        return;
      } else {
        alert("로그인이 잠겨 있습니다.");
        return;
      }
    }

    if (error === "BAD_CREDENTIALS") {
      if (remainingAttempts !== undefined && remainingAttempts !== null) {
        alert(`아이디 또는 비밀번호가 틀립니다.\n남은 시도 횟수: ${remainingAttempts}회`);
        return;
      } else if (message) {
        alert(message);
        return;
      } else {
        alert("아이디 또는 비밀번호가 틀립니다.");
        return;
      }
    }

    if (message) {
      alert(message);
      return;
    }

    if (error === "PENDING_APPROVAL") {
      alert("현재 승인 대기 상태입니다.");
    } else if (error === "DELETED_ACCOUNT") {
      alert("탈퇴된 계정입니다.");
    } else if (error === "ERROR_LOGIN") {
      alert(message || "로그인에 실패했습니다.");
    } else {
      alert("로그인 실패: " + (error || "알 수 없는 오류"));
    }
  };

  const handleClickLogin = (e) => {
    if (e) e.preventDefault();
    doLogin(loginParam)
      .then((data) => {
        if (data && data.error) {
          handleError(data);
        } else {
          moveToPath("/");
          // 로그인 직후 게스트 카트 → 회원 카트 병합 (서버에서 성공 시 guest_token 쿠키 삭제)
          mergeCart()
            .catch(() => { /* 병합 실패해도 로그인은 유지 */ })
            .finally(() => moveToPath("/"));
        }
      })
      .catch((err) => {
        const errorData = (err && typeof err === 'object') ? err : { error: "로그인에 실패했습니다.", message: "로그인에 실패했습니다." };
        handleError(errorData);
      });
  };

  return (
    <div className="ui-card p-8 lg:p-10">
      <div className="flex flex-col items-center mb-8">
        <div className="text-xs uppercase tracking-widest text-baseMuted mb-2">LOGIN</div>
        <h1 className="ui-title">로그인</h1>
      </div>

      <form className="space-y-4" onSubmit={handleClickLogin}>
        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">이메일</label>
          <input className="ui-input" name="email" type="text" value={loginParam.email} onChange={handleChange} />
        </div>
        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">비밀번호</label>
          <input className="ui-input" name="pw" type="password" value={loginParam.pw} onChange={handleChange} />
        </div>

        <div className="flex flex-col gap-3 pt-4">
          <div className="flex gap-3">
            <button type="submit" className="flex-1 ui-btn-primary">로그인</button>
            <button type="button" className="flex-1 ui-btn-secondary" onClick={() => moveToPath("/member/join")}>회원가입</button>
          </div>
        </div>
      </form>

      <div className="relative my-8">
        <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-baseBorder"></div></div>
        <div className="relative flex justify-center text-xs font-semibold"><span className="bg-baseBg px-4 text-baseMuted">소셜 로그인</span></div>
      </div>
      <KakaoLoginComponent />
    </div>
  );
};

export default LoginComponent;