import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { loginPost, logoutPost } from "../api/memberApi";
import { getCookie, removeCookie, setCookie } from "../util/cookieUtil";

// Redux Toolkit을 이용한 로그인 상태 관리(slice)
// React 컴포넌트들은 Redux store의 slice state를 읽어서 로그인 상태를 판단 -> reducer내부의 함수를 통해 쿠키 설정 및 state 변환(로그인 상태)

const initState = {
  email: "",
};
const loadMemberCookie = () => {
  // 쿠키에서 로그인 정보 로딩 ('member' 이름의 쿠키 반환)
  const memberInfo = getCookie("member");

  //닉네임 처리하여 사용자가 입력한 값중에 특수문자나 공백이 포함되면 디코딩하여 제대로 된 형태로 표시
  if (memberInfo && memberInfo.name) {
    memberInfo.name = decodeURIComponent(memberInfo.name);
  }
  return memberInfo;
};

// 비동기 로그인 처리 (일반 로그인) - loginPost 실행. 자동으로 아래 상태 생성됨: pending, fulfilled, rejected
export const loginPostAsync = createAsyncThunk("loginPostAsync", async (param, { rejectWithValue }) => {
  try {
    const result = await loginPost(param);
    // 백엔드가 HTTP 200으로 에러 응답을 보낼 수 있음 (error 필드가 있으면 에러)
    if (result && result.error) {
      return rejectWithValue(result);
    }
    return result;
  } catch (err) {
    // 네트워크 에러 등 (HTTP 에러가 아닌 경우)
    return rejectWithValue(err.response?.data || { error: "로그인에 실패했습니다." });
  }
});

// 비동기 로그아웃 처리 - logoutPost 실행하여 백엔드에 Refresh Token 삭제 요청
export const logoutPostAsync = createAsyncThunk("logoutPostAsync", async (_, { rejectWithValue }) => {
  try {
    await logoutPost();
    return { success: true };
  } catch (err) {
    // API 호출 실패해도 프론트엔드 로그아웃은 진행
    console.error("Logout API error:", err);
    // 에러가 발생해도 성공으로 처리 (프론트엔드 로그아웃은 진행)
    return { success: true };
  }
});

// loginSlice : 로그인 상태를 관리하는 Redux 상태 묶음(모듈)  - 상태(state) + reducer들 + action 생성기들을 한 번에 묶어 놓은 객체
// 실행되는 코드가 아니라, Redux 설정용 “설계도 객체”
const loginSlice = createSlice({
  // createSlice : Redux에서 필요한 걸 한 번에 만들어줌
  name: "LoginSlice",
  // loadMemberCookie() 실행, 값이 “truthy”(true로 평가되는 값) 이면 → 그 값을 사용, 그렇지 않으면 → initState 사용
  initialState: (() => {
    try {
      return loadMemberCookie() || initState;
    } catch (_) {
      return initState;
    }
  })(),
  reducers: {
    // 이 slice가 직접 정의한 action + reducer
    login: (state, action) => {
      // 소셜 로그인 회원이 사용
      const payload = action.payload;
      setCookie("member", JSON.stringify(payload), 1); // 1일 - 로그인 정보를 쿠키에 저장

      // slice의 state를 payload로 변환
      if (payload?.accessToken && typeof localStorage !== "undefined") {
        try {
          localStorage.setItem("accessToken", payload.accessToken);
        } catch (_) {}
      }
      return payload;
    },
  },
  extraReducers: (builder) => {
    // slice의 기본 reducers가 아닌, 외부에서 만든 액션(createAsyncThunk)에 대응할 때 사용
    builder
      .addCase(loginPostAsync.fulfilled, (state, action) => {
        const payload = action.payload;

        // 정상적인 로그인시에만 저장 (error 필드가 없을 때만)
        if (payload && !payload.error) {
          setCookie("member", JSON.stringify(payload), 1); //1일
          if (payload.accessToken && typeof localStorage !== "undefined") {
            try {
              localStorage.setItem("accessToken", payload.accessToken);
            } catch (_) {}
          }
        }

        return payload;
      })
      .addCase(loginPostAsync.rejected, (state, action) => {
        // 에러 응답 데이터를 state에 저장 (LoginComponent에서 catch로 받을 수 있도록)
        // action.payload에 { error, message, remainingMinutes } 등이 포함됨
        if (action.payload && typeof action.payload === 'object') {
          return action.payload;
        }
        return { ...initState, error: "로그인에 실패했습니다.", message: "로그인에 실패했습니다." };
      })
      .addCase(logoutPostAsync.rejected, (state, action) => {
        // 로그아웃 API 실패해도 프론트엔드 로그아웃은 진행
        removeCookie("member");
        removeCookie("refreshToken");

        // 로컬/세션 스토리지 정리
        localStorage.removeItem("accessToken");
        localStorage.removeItem("member");
        sessionStorage.clear();

        return { ...initState };
      });
  },
});

export const { login } = loginSlice.actions;
// logout은 logoutPostAsync를 사용하도록 변경
export const logout = () => logoutPostAsync();
export default loginSlice.reducer;