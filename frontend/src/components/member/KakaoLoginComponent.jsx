import { Link } from "react-router-dom";
import { getKakaoLoginLink } from "../../api/kakaoApi";

const KakaoLoginComponent = () => {
  const link = getKakaoLoginLink();

  return (
    <Link to={link} className="block">
      <button className="ui-btn-kakao w-full flex justify-center items-center gap-3">
        <span className="text-2xl">💬</span>
        <span className="font-semibold text-base uppercase">카카오 로그인</span>
      </button>
    </Link>
  );
};

export default KakaoLoginComponent;