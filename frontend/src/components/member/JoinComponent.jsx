import { useState } from "react";
import { joinPost } from "../../api/memberApi";
import useCustomLogin from "../../hooks/useCustomLogin";
import { validatePassword, getPasswordPolicyText } from "../../util/passwordValidator";

const initState = {
  email: "",
  pw: "",
  name: "",
  gender: "MALE",
  birthDate:"",
  height: "",
  weight: "",
};

const JoinComponent = () => {
  const [joinParam, setJoinParam] = useState({ ...initState });
  const [passwordError, setPasswordError] = useState(null);
  const { moveToLogin } = useCustomLogin();

  const handleChange = (e) => {
    // 상태 업데이트 방식 개선 (직접 변경 대신 setState 사용)
    const newValue = e.target.value;
    setJoinParam({
      ...joinParam,
      [e.target.name]: newValue
    });

    // 비밀번호 실시간 검증 (입력 중)
    if (e.target.name === "pw") {
      if (newValue && newValue.trim() !== "") {
        const validation = validatePassword(newValue);
        setPasswordError(validation.valid ? null : validation.message);
      } else {
        setPasswordError(null);
      }
    }
  };

  const handleClickJoin = (e) => {
    if(e) e.preventDefault();

    // #region agent log
    fetch('http://127.0.0.1:7242/ingest/95aec53f-1cc8-4098-a3e8-d29a7d621e66',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sessionId:'debug-session',runId:'pre-fix',hypothesisId:'C',location:'src/components/member/JoinComponent.jsx:handleClickJoin',message:'submit clicked',data:{keys:Object.keys(joinParam||{}),hasRequired:{email:!!joinParam?.email,pw:!!joinParam?.pw,name:!!joinParam?.name,gender:!!joinParam?.gender,birthDate:!!joinParam?.birthDate}},timestamp:Date.now()})}).catch(()=>{});
    // #endregion

    if (!joinParam.email || !joinParam.pw || !joinParam.name) {
      alert("모든 정보를 입력해주세요.");
      return;
    }

    // 비밀번호 정책 검증
    const passwordValidation = validatePassword(joinParam.pw);
    if (!passwordValidation.valid) {
      alert(passwordValidation.message);
      return;
    }

    joinPost(joinParam)
      .then((result) => {
        if (result.result === "success") {
          alert("회원가입이 완료되었습니다.\n관리자 승인 후 로그인이 가능합니다.");
          moveToLogin();
        }
      })
      .catch((err) => {
          const errorCode = err?.response?.data?.code;

          if (errorCode === "DELETED_ACCOUNT") {
            alert("이미 존재하는 이메일 입니다");
          } else {
            alert("회원가입 실패. 다시 시도해주세요.");
          }
      });
  };

  return (
    <div className="ui-card p-8 lg:p-10">
      <div className="flex flex-col items-center mb-8">
        <div className="text-xs uppercase tracking-widest text-baseMuted mb-2">JOIN</div>
        <h1 className="ui-title">회원가입</h1>
        <p className="text-baseMuted text-xs mt-2">전문 계정을 생성하세요</p>
      </div>

      <form className="space-y-4" onSubmit={handleClickJoin}>
        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">이메일</label>
          <input
            className="ui-input"
            name="email" type="text" onChange={handleChange} placeholder="example@domain.com"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">비밀번호</label>
          <input
            className={`ui-input ${passwordError ? "border-red-500" : ""}`}
            name="pw" type="password" onChange={handleChange} placeholder="비밀번호를 입력하세요"
          />
          {passwordError && (
            <p className="text-xs text-red-500 mt-1">{passwordError}</p>
          )}
          {!passwordError && joinParam.pw && (
            <p className="text-xs text-green-600 mt-1">✓ 비밀번호 규칙을 만족합니다</p>
          )}
          {!joinParam.pw && (
            <p className="text-xs text-baseMuted mt-1">{getPasswordPolicyText()}</p>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">이름</label>
          <input
            className="ui-input"
            name="name" type="text" onChange={handleChange} placeholder="이름을 입력하세요"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">성별</label>
          <select
            name="gender"
            value={joinParam.gender}
            onChange={handleChange}
            className="ui-select"
          >
            <option value="MALE">남</option>
            <option value="FEMALE">여</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">생년월일</label>
          <input
            className="ui-input"
            name="birthDate"
            type="text"
            onChange={handleChange}
            placeholder="YYYY-MM-DD (예: 2001-10-09)"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">키(cm)</label>
          <input
            className="ui-input"
            name="height"
            type="number"
            onChange={handleChange}
            placeholder="예: 173"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">몸무게(kg)</label>
          <input
            className="ui-input"
            name="weight"
            type="number"
            step="0.1"
            onChange={handleChange}
            placeholder="예: 76"
          />
        </div>


        <button
          className="w-full ui-btn-primary py-4 mt-6"
          type="submit"
        >
          계정 생성
        </button>
      </form>
    </div>
  );
};

export default JoinComponent;