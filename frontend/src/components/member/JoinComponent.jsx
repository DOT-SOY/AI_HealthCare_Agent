import { useState } from "react";
import { joinPost, checkEmail } from "../../api/memberApi";
import useCustomLogin from "../../hooks/useCustomLogin";
import { validatePassword, getPasswordPolicyText } from "../../util/passwordValidator";

const initState = {
  email: "",
  pw: "",
  name: "",
  gender: "MALE",
  birthDate: "",
  height: "",
  weight: "",
};

const TOTAL_STEPS = 3;

const JoinComponent = () => {
  const [step, setStep] = useState(1);
  const [joinParam, setJoinParam] = useState({ ...initState });
  const [passwordError, setPasswordError] = useState(null);
  const [emailCheckStatus, setEmailCheckStatus] = useState({
    checked: false,
    available: null,
    message: "",
    checking: false,
  });
  const { moveToLogin } = useCustomLogin();

  const handleChange = (e) => {
    const newValue = e.target.value;
    setJoinParam({
      ...joinParam,
      [e.target.name]: newValue,
    });

    if (e.target.name === "email") {
      setEmailCheckStatus({
        checked: false,
        available: null,
        message: "",
        checking: false,
      });
    }

    if (e.target.name === "pw") {
      if (newValue && newValue.trim() !== "") {
        const validation = validatePassword(newValue);
        setPasswordError(validation.valid ? null : validation.message);
      } else {
        setPasswordError(null);
      }
    }
  };

  const handleCheckEmail = async () => {
    if (!joinParam.email || !joinParam.email.trim()) {
      alert("이메일을 입력해주세요.");
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(joinParam.email)) {
      alert("올바른 이메일 형식을 입력해주세요.");
      return;
    }

    setEmailCheckStatus((prev) => ({ ...prev, checking: true, message: "" }));

    try {
      const result = await checkEmail(joinParam.email);
      if (result.error) {
        setEmailCheckStatus({
          checked: false,
          available: null,
          message: result.error,
          checking: false,
        });
        return;
      }
      const isAvailable = result.available === true;
      setEmailCheckStatus({
        checked: true,
        available: isAvailable,
        message:
          result.message ||
          (isAvailable ? "사용 가능한 이메일입니다." : "이메일이 이미 사용중입니다."),
        checking: false,
      });
    } catch (err) {
      const raw = err?.response?.data?.message || err?.response?.data?.error || "";
      const isEnglishCode = ["UNAUTHORIZED", "ERROR_ACCESS_TOKEN"].includes(raw);
      setEmailCheckStatus({
        checked: false,
        available: null,
        message: isEnglishCode
          ? "중복확인에 실패했습니다. 다시 시도해주세요."
          : raw || "중복확인에 실패했습니다. 다시 시도해주세요.",
        checking: false,
      });
    }
  };

  const canGoStep3 = () => {
    const name = (joinParam.name || "").trim();
    const birth = (joinParam.birthDate || "").trim();
    return name.length >= 2 && /^\d{4}-\d{2}-\d{2}$/.test(birth) && joinParam.gender;
  };

  const handleNext = (e) => {
    if (e) e.preventDefault();
    if (step === 1) {
      if (!joinParam.email || !joinParam.pw) {
        alert("이메일과 비밀번호를 입력해주세요.");
        return;
      }
      if (!emailCheckStatus.checked || !emailCheckStatus.available) {
        alert("이메일 중복확인을 해주세요.");
        return;
      }
      const pv = validatePassword(joinParam.pw);
      if (!pv.valid) {
        alert(pv.message);
        return;
      }
      setStep(2);
    } else if (step === 2) {
      if (!canGoStep3()) {
        alert("이름, 생년월일(YYYY-MM-DD), 성별을 모두 입력해주세요.");
        return;
      }
      setStep(3);
    }
  };

  const handlePrev = (e) => {
    if (e) e.preventDefault();
    setStep((s) => Math.max(1, s - 1));
  };

  const handleClickJoin = (e) => {
    if (e) e.preventDefault();

    if (!joinParam.email || !joinParam.pw || !joinParam.name) {
      alert("모든 정보를 입력해주세요.");
      return;
    }
    if (!emailCheckStatus.checked || !emailCheckStatus.available) {
      alert("이메일 중복확인을 해주세요.");
      return;
    }
    const passwordValidation = validatePassword(joinParam.pw);
    if (!passwordValidation.valid) {
      alert(passwordValidation.message);
      return;
    }
    if (!joinParam.birthDate || !/^\d{4}-\d{2}-\d{2}$/.test(joinParam.birthDate)) {
      alert("생년월일을 YYYY-MM-DD 형식으로 입력해주세요.");
      return;
    }
    const heightNum = joinParam.height !== "" && joinParam.height != null ? Number(joinParam.height) : null;
    const weightNum = joinParam.weight !== "" && joinParam.weight != null ? Number(joinParam.weight) : null;
    if (heightNum == null || weightNum == null) {
      alert("키와 몸무게를 입력해주세요.");
      return;
    }

    const payload = {
      ...joinParam,
      height: heightNum,
      weight: weightNum,
    };

    joinPost(payload)
      .then((result) => {
        if (result.result === "success") {
          alert("회원가입이 완료되었습니다.");
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
      <div className="flex flex-col items-center mb-6">
        <div className="text-xs uppercase tracking-widest text-baseMuted mb-2">JOIN</div>
        <h1 className="ui-title">회원가입</h1>
        <p className="text-baseMuted text-xs mt-2">
          {step === 1 && "계정을 생성하세요"}
          {step === 2 && "기본 정보를 입력해주세요"}
          {step === 3 && "신체 정보를 입력해주세요"}
        </p>
      </div>

      {/* 진행 표시 */}
      <div className="flex gap-2 mb-6">
        {[1, 2, 3].map((s) => (
          <div
            key={s}
            className={`h-1 flex-1 rounded-full ${
              step >= s ? "bg-green-500" : "bg-gray-200 dark:bg-gray-700"
            }`}
          />
        ))}
      </div>

      <form className="space-y-4" onSubmit={step === 3 ? handleClickJoin : handleNext}>
        {/* Step 1: 이메일, 비밀번호 (중복확인·비밀번호 검증 유지) */}
        {step === 1 && (
          <>
            <div>
              <label className="block text-xs font-semibold text-baseMuted mb-2">이메일</label>
              <div className="flex gap-2">
                <input
                  className={`ui-input flex-1 ${
                    emailCheckStatus.checked && !emailCheckStatus.available
                      ? "border-red-500"
                      : emailCheckStatus.checked && emailCheckStatus.available
                        ? "border-green-500"
                        : ""
                  }`}
                  name="email"
                  type="text"
                  value={joinParam.email}
                  onChange={handleChange}
                  placeholder="example@domain.com"
                />
                <button
                  type="button"
                  onClick={handleCheckEmail}
                  disabled={emailCheckStatus.checking || !joinParam.email}
                  className="ui-btn-ghost text-xs px-4 py-2 whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {emailCheckStatus.checking ? "확인 중..." : "중복확인"}
                </button>
              </div>
              {emailCheckStatus.message && (
                <p
                  className={`text-xs mt-1 ${
                    emailCheckStatus.available ? "text-green-600" : "text-red-500"
                  }`}
                >
                  {emailCheckStatus.available ? "✓ " : "✗ "}
                  {emailCheckStatus.message}
                </p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-baseMuted mb-2">비밀번호</label>
              <input
                className={`ui-input ${passwordError ? "border-red-500" : ""}`}
                name="pw"
                type="password"
                onChange={handleChange}
                placeholder="비밀번호를 입력하세요"
              />
              {passwordError && <p className="text-xs text-red-500 mt-1">{passwordError}</p>}
              {!passwordError && joinParam.pw && (
                <p className="text-xs text-green-600 mt-1">✓ 비밀번호 규칙을 만족합니다</p>
              )}
              {!joinParam.pw && (
                <p className="text-xs text-baseMuted mt-1">{getPasswordPolicyText()}</p>
              )}
            </div>
          </>
        )}

        {/* Step 2: 이름, 생년월일, 성별 */}
        {step === 2 && (
          <>
            <div>
              <label className="block text-xs font-semibold text-baseMuted mb-2">이름</label>
              <input
                className="ui-input"
                name="name"
                type="text"
                value={joinParam.name}
                onChange={handleChange}
                placeholder="이름을 입력하세요"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-baseMuted mb-2">생년월일</label>
              <input
                className="ui-input"
                name="birthDate"
                type="text"
                value={joinParam.birthDate}
                onChange={handleChange}
                placeholder="YYYY-MM-DD (예: 2001-10-09)"
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
          </>
        )}

        {/* Step 3: 키, 몸무게 */}
        {step === 3 && (
          <>
            <div>
              <label className="block text-xs font-semibold text-baseMuted mb-2">키(cm)</label>
              <input
                className="ui-input"
                name="height"
                type="number"
                value={joinParam.height}
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
                value={joinParam.weight}
                onChange={handleChange}
                placeholder="예: 76"
              />
            </div>
          </>
        )}

        {/* 버튼 */}
        <div className="flex gap-2 mt-6">
          {step > 1 && (
            <button type="button" onClick={handlePrev} className="ui-btn-ghost flex-1 py-3">
              이전
            </button>
          )}
          {step < TOTAL_STEPS ? (
            <button type="submit" className="ui-btn-primary flex-1 py-3">
              다음
            </button>
          ) : (
            <button type="submit" className="ui-btn-primary flex-1 py-3">
              가입 완료
            </button>
          )}
        </div>
      </form>
    </div>
  );
};

export default JoinComponent;
