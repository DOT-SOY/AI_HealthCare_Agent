import BasicMenu from "../menu/BasicMenu";
import { Link } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { logout } from "../../slices/loginSlice";
import AIChatOverlay from '../../pages/AIChatOverlay';
import ResetStyles from '../common/ResetStyles';

const BasicLayout = ({ children }) => {
  const dispatch = useDispatch();
  const loginState = useSelector((state) => state.loginSlice);
  const isLogin = !!loginState?.email;

  const handleClickLogout = async () => {
    await dispatch(logout());
  };

  return (
    <>
      <BasicMenu />
      <div className="lg:ml-64">
        {/* 상단 헤더 (오른쪽 위: 이름/로그아웃) */}
        <header className="sticky top-0 z-40 bg-white/95 backdrop-blur border-b border-gray-200">
          <div className="h-14 px-4 flex items-center justify-end">
            {!isLogin ? (
              <Link to="/member/login" className="ui-btn-primary text-sm px-4 py-2">
                Login
              </Link>
            ) : (
              <div className="flex items-center gap-3">
                <div className="hidden sm:flex flex-col items-end leading-tight">
                  <span className="text-xs text-gray-500">Welcome</span>
                  <span className="text-sm font-semibold text-gray-900">
                    {(loginState?.name || loginState?.email) ? `${loginState?.name || loginState?.email}님` : ""}
                  </span>
                </div>
                <button onClick={handleClickLogout} className="ui-btn-ghost text-xs px-3 py-2">
                  Logout
                </button>
              </div>
            )}
          </div>
        </header>

        <main className="pt-4">
          {children}
        </main>
      </div>
      <AIChatOverlay />
    </>
  );
};

export default BasicLayout;
