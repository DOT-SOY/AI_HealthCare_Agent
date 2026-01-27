import axios from "axios";
const API_SERVER_HOST =
  import.meta.env.VITE_API_SERVER_HOST || "http://localhost:8080";

const rest_api_key = import.meta.env.VITE_KAKAO_REST_API_KEY;
const redirect_uri = import.meta.env.VITE_KAKAO_REDIRECT_URI;
// (선택) 카카오 개발자 콘솔에서 "Client Secret 사용"을 켠 경우 필요
const client_secret = import.meta.env.VITE_KAKAO_CLIENT_SECRET;

const auth_code_path = `https://kauth.kakao.com/oauth/authorize`;
const access_token_url = `https://kauth.kakao.com/oauth/token`;

// 카카오 로그인 페이지로 이동할 URL 생성, 브라우저에서 해당 URL로 이동하면 카카오 로그인 후 redirect_uri로 인증 코드 전송
export const getKakaoLoginLink = () => {
  const kakaoURL = `${auth_code_path}?client_id=${rest_api_key}&redirect_uri=${redirect_uri}&response_type=code`;

  return kakaoURL;
};

// 카카오 로그인 후 받은 인증 코드(authCode) 를 서버에 보내지 않고 프론트에서 직접 액세스 토큰 요청, 요청 성공 시 access_token 반환
export const getAccessToken = async (authCode) => {
  if (!rest_api_key || !redirect_uri) {
    throw new Error("카카오 환경변수(VITE_KAKAO_REST_API_KEY / VITE_KAKAO_REDIRECT_URI)가 설정되지 않았습니다.");
  }

  const header = { headers: { "Content-Type": "application/x-www-form-urlencoded;charset=utf-8" } };
  // axios가 JSON으로 보내지 않도록 x-www-form-urlencoded 형식으로 직렬화하기 위해 URLSearchParams 사용
  const paramsObj = {
    grant_type: "authorization_code",
    client_id: rest_api_key,
    redirect_uri,
    code: authCode,
  };

  // Client Secret 사용 시에만 포함
  if (client_secret) {
    paramsObj.client_secret = client_secret;
  }

  const params = new URLSearchParams(paramsObj);

  try {
    const res = await axios.post(access_token_url, params, header);

    const accessToken = res.data.access_token;

    if (!accessToken) {
      throw new Error("카카오 access_token이 응답에 없습니다.");
    }

    return accessToken;
  } catch (e) {
    // 카카오 토큰 API 에러를 최대한 그대로 보여주기
    const status = e?.response?.status;
    const data = e?.response?.data;
    const msg =
      data?.error_description ||
      data?.error ||
      e?.message ||
      "카카오 토큰 발급에 실패했습니다.";
    const err = new Error(status ? `[${status}] ${msg}` : msg);
    err.data = data;
    throw err;
  }
};
// 백엔드 Spring Controller (SocialController) 호출 - 전달: 카카오 accessToken
export const getMemberWithAccessToken = async (accessToken) => {
  const res = await axios.get(`${API_SERVER_HOST}/api/member/kakao`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    withCredentials: true,
  });

  return res.data; // claims 반환
};