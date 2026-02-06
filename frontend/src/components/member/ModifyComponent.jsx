import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { modifyMember, withdrawMember } from "../../api/memberApi";
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

const ModifyComponent = () => {
  const [member, setMember] = useState({ ...initState });
  const [passwordError, setPasswordError] = useState(null);
  const loginInfo = useSelector((state) => state.loginSlice);
  const { moveToLogin, doLogout } = useCustomLogin();

  useEffect(() => {
    setMember((prev) => ({
      ...prev,
      email: loginInfo.email,
      pw: "",
      name: loginInfo.name || "",
    }));
  }, [loginInfo]);

  const handleChange = (e) => {
    const newValue = e.target.value;
    setMember({ ...member, [e.target.name]: newValue });

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

  const handleClickModify = (e) => {
    if (e) e.preventDefault();

    if (!member.name) {
      alert("이름은 필수 입력 항목입니다.");
      return;
    }

    if (!member.gender) {
      alert("성별은 필수 입력 항목입니다.");
      return;
    }

    if (!member.birthDate) {
      alert("생년월일은 필수 입력 항목입니다.");
      return;
    }

    if (!member.height) {
      alert("키는 필수 입력 항목입니다.");
      return;
    }

    if (!member.weight) {
      alert("몸무게는 필수 입력 항목입니다.");
      return;
    }

    if (!member.pw || member.pw.trim() === "") {
      alert("비밀번호를 입력해야 정보 수정이 가능합니다.");
      return;
    }

    // 비밀번호 정책 검증
    const passwordValidation = validatePassword(member.pw);
    if (!passwordValidation.valid) {
      alert(passwordValidation.message);
      return;
    }

    const memberToSend = {
      name: member.name,
      gender: member.gender,
      birthDate: member.birthDate,
      height: Number(member.height),
      weight: Number(member.weight),
      pw: member.pw,
    };

    modifyMember(memberToSend)
      .then(async (result) => {
        alert("정보 수정이 완료되었습니다.\n비밀번호가 변경되어 모든 기기에서 로그아웃됩니다.");
        await doLogout();
        moveToLogin();
      })
      .catch((err) => {
        alert("수정 중 오류가 발생했습니다.");
      });
  };

  // 회원 탈퇴 처리
  const handleClickWithdraw = async (e) => {
    if (e) e.preventDefault();

    const confirmMessage = "정말 회원 탈퇴를 진행하시겠습니까?\n탈퇴 후에는 복구할 수 없습니다.";
    if (!window.confirm(confirmMessage)) {
      return;
    }

    try {
      const result = await withdrawMember();
      alert(result?.message || "회원 탈퇴가 완료되었습니다.");

      // 탈퇴 성공 시 로그아웃 및 로그인 페이지 이동
      await doLogout();
      moveToLogin();
    } catch (err) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        "회원 탈퇴 처리 중 오류가 발생했습니다.";
      alert(msg);
    }
  };

  return (
    <div className="ui-card p-8 lg:p-10">
      <div className="flex flex-col items-center mb-8">
        <div className="text-xs uppercase tracking-widest text-baseMuted mb-2">PROFILE</div>
        <h1 className="ui-title">프로필 수정</h1>
        <p className="text-baseMuted text-xs mt-2">개인 정보를 업데이트하세요</p>
      </div>

      <form className="space-y-4" onSubmit={handleClickModify}>
        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">이메일 (읽기 전용)</label>
          <input
            className="ui-input bg-baseSurface text-baseMuted"
            name="email" type="text" value={member.email} readOnly
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">새 비밀번호</label>
          <input
            className={`ui-input ${passwordError ? "border-red-500" : ""}`}
            name="pw" type="password" value={member.pw} onChange={handleChange} placeholder="새 비밀번호를 입력하세요"
          />
          {passwordError && (
            <p className="text-xs text-red-500 mt-1">{passwordError}</p>
          )}
          {!passwordError && member.pw && (
            <p className="text-xs text-green-600 mt-1">✓ 비밀번호 규칙을 만족합니다</p>
          )}
            <p className="text-xs text-baseMuted mt-1">{getPasswordPolicyText()}</p>
        </div>

        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">이름</label>
          <input
            className="ui-input"
            name="name" type="text" value={member.name} onChange={handleChange}
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-baseMuted mb-2">성별</label>
          <select
            name="gender"
            value={member.gender}
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
            value={member.birthDate}
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
            value={member.height}
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
            value={member.weight}
            onChange={handleChange}
            placeholder="예: 76"
          />
        </div>

        <button
          className="w-full ui-btn-primary py-4 mt-6"
          type="submit"
        >
          업데이트 및 재승인
        </button>
      </form>

      {/* 회원 탈퇴 섹션 */}
      <div className="mt-8 pt-6 border-t border-gray-700">
        <button
          type="button"
          onClick={handleClickWithdraw}
          className="w-full py-3 px-4 text-sm text-red-400 hover:text-red-300 border border-red-500/50 hover:border-red-500 rounded-lg transition-colors"
        >
          회원 탈퇴
        </button>
        <p className="text-xs text-baseMuted mt-2 text-center">
          탈퇴 후에는 계정을 복구할 수 없습니다.
        </p>
      </div>
    </div>
  );
};

export default ModifyComponent;