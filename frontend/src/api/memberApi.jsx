import axios from "axios";
import jwtAxios from "../util/jwtUtil";

const API_SERVER_HOST =
  import.meta.env.VITE_API_SERVER_HOST || "http://localhost:8080";


const host = `${API_SERVER_HOST}/api/member`;

// /login API를 호출해서 로그인 처리, 성공 시 서버에서 보내주는 JWT 토큰, 사용자 정보를 받음
// 서버에서는 컨트롤러 없이도 Spring Security Filter Chain이 요청을 가로챔(config.loginPage()루트와 동일) - loadUserByUsername 실행
export const loginPost = async (loginParam) => {
  // #region agent log
  fetch('http://127.0.0.1:7242/ingest/95aec53f-1cc8-4098-a3e8-d29a7d621e66',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sessionId:'debug-session',runId:'pre-fix',hypothesisId:'B',location:'src/api/memberApi.jsx:loginPost',message:'loginPost called',data:{apiServerHost:API_SERVER_HOST,host,hasEmail:!!loginParam?.email,hasPw:!!loginParam?.pw},timestamp:Date.now()})}).catch(()=>{});
  // #endregion
  const header = { headers: { "Content-Type": "x-www-form-urlencoded" } };

  const form = new FormData();
  form.append("username", loginParam.email);
  form.append("password", loginParam.pw);

  try {
    const res = await axios.post(`${host}/login`, form, { ...header, withCredentials: true });
    // 백엔드가 HTTP 200으로 에러 응답을 보낼 수 있음 (error 필드 포함)
    return res.data;
  } catch (err) {
    // HTTP 에러 상태 코드를 받은 경우 (4xx, 5xx)
    if (err.response && err.response.data) {
      return err.response.data; // 에러 응답 body를 반환
    }
    // 네트워크 에러 등
    throw err;
  }
};

export const modifyMember = async (member) => {
  const res = await jwtAxios.put(`${host}/modify`, member);

  return res.data;
};

export const joinPost = async (joinParam) => {
  const header = { headers: { "Content-Type": "application/json" } };
  // #region agent log
  fetch('http://127.0.0.1:7242/ingest/95aec53f-1cc8-4098-a3e8-d29a7d621e66',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sessionId:'debug-session',runId:'pre-fix',hypothesisId:'A',location:'src/api/memberApi.jsx:joinPost',message:'joinPost called',data:{apiServerHost:API_SERVER_HOST,host,keys:Object.keys(joinParam||{}),hasRequired:{email:!!joinParam?.email,pw:!!joinParam?.pw,name:!!joinParam?.name,gender:!!joinParam?.gender,birthDate:!!joinParam?.birthDate}},timestamp:Date.now()})}).catch(()=>{});
  // #endregion

  // JSON 형태로 전송
  try {
    const res = await axios.post(`${host}/join`, joinParam, header);
    // #region agent log
    fetch('http://127.0.0.1:7242/ingest/95aec53f-1cc8-4098-a3e8-d29a7d621e66',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sessionId:'debug-session',runId:'pre-fix',hypothesisId:'A',location:'src/api/memberApi.jsx:joinPost',message:'joinPost success',data:{status:res?.status},timestamp:Date.now()})}).catch(()=>{});
    // #endregion
    return res.data;
  } catch (err) {
    // #region agent log
    fetch('http://127.0.0.1:7242/ingest/95aec53f-1cc8-4098-a3e8-d29a7d621e66',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sessionId:'debug-session',runId:'pre-fix',hypothesisId:'A',location:'src/api/memberApi.jsx:joinPost',message:'joinPost error',data:{code:err?.code,message:err?.message,hasResponse:!!err?.response,status:err?.response?.status},timestamp:Date.now()})}).catch(()=>{});
    // #endregion
    throw err;
  }
};

// 멤버 검색 (일반 사용자용)
export const searchMembers = async (keyword, page = 1, size = 20, department = null) => {
  const params = {
    page,
    size,
    keyword: keyword || null,
    department: department || null
  };

  const res = await jwtAxios.get(`${host}/search`, { params });
  return res.data;
};

// 로그아웃 API - Refresh Token 삭제
export const logoutPost = async () => {
  const res = await jwtAxios.post(`${host}/logout`);
  return res.data;
};

// 담당자 정보 조회 (email로 부서, 닉네임 조회)
export const getMemberInfo = async (email) => {
  const res = await jwtAxios.get(`${host}/info/${email}`);
  return res.data;
};