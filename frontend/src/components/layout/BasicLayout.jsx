import BasicMenu from "../menu/BasicMenu";
import { Link } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { Lock, LogOut } from "lucide-react";
import { logout } from "../../slices/loginSlice";
import AIChatOverlay from '../../pages/AIChatOverlay';
import ResetStyles from '../common/ResetStyles';

const BasicLayout = ({ children, containerClassName = "page-container" }) => {
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
        {/* 상단 헤더 (오른쪽: 로그인/로그아웃 아이콘) */}
        <header className="sticky top-0 z-40 bg-bg-root border-b border-border-default">
          <div className="h-14 px-4 flex items-center justify-end">
            {!isLogin ? (
              <Link to="/member/login" className="p-2 rounded-token-sm text-primary-500 hover:text-primary-400 hover:bg-bg-surface/50 transition-colors" aria-label="로그인">
                <Lock className="w-5 h-5" strokeWidth={2} />
              </Link>
            ) : (
              <button
                type="button"
                onClick={handleClickLogout}
                className="p-2 rounded-token-sm text-primary-500 hover:text-primary-400 hover:bg-bg-surface/50 transition-colors"
                aria-label="로그아웃"
              >
                <LogOut className="w-5 h-5" strokeWidth={2} />
              </button>
            )}
          </div>
        </header>

        <main>
          <div className="page-root">
            <div className={containerClassName}>
              {children}
            </div>
          </div>
        </main>
      </div>
      <AIChatOverlay />
    </>
  );
};

export default BasicLayout;
