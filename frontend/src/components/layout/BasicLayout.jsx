import { useState } from "react";
import BasicMenu from "../menu/BasicMenu";
import { Link } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { Lock, LogOut, Menu, PanelLeftClose, PanelLeftOpen, X } from "lucide-react";
import { logout } from "../../slices/loginSlice";
import AIChatOverlay from '../../pages/AIChatOverlay';
import ResetStyles from '../common/ResetStyles';

const BasicLayout = ({ children, containerClassName = "page-container" }) => {
  const dispatch = useDispatch();
  const loginState = useSelector((state) => state.loginSlice);
  const isLogin = !!loginState?.email;
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [isMobileMenuOpen, setMobileMenuOpen] = useState(false);

  const handleClickLogout = async () => {
    await dispatch(logout());
  };

  const toggleSidebar = () => setIsSidebarOpen((prev) => !prev);

  return (
    <>
      <BasicMenu
        isSidebarOpen={isSidebarOpen}
        isMobileMenuOpen={isMobileMenuOpen}
        onCloseMobileMenu={() => setMobileMenuOpen(false)}
      />
      <div
        className={`transition-[margin-left] duration-300 ease-[cubic-bezier(0.25,1,0.5,1)] ${
          isSidebarOpen ? "lg:ml-64" : "lg:ml-0"
        }`}
      >
        {/* 상단 헤더 */}
        <header className="sticky top-0 z-40 bg-bg-root border-b border-border-default">
          <div className="h-14 px-4 flex items-center">
            {/* 테블릿/모바일: 메뉴 트리거 (Welcome 왼쪽) */}
            <button
              type="button"
              onClick={() => setMobileMenuOpen((prev) => !prev)}
              className="lg:hidden p-2 -ml-2 mr-2 rounded-token-sm text-text-sub hover:text-primary-500 hover:bg-bg-surface/50 transition-colors"
              aria-label={isMobileMenuOpen ? "메뉴 닫기" : "메뉴 열기"}
            >
              {isMobileMenuOpen ? (
                <X className="w-6 h-6" strokeWidth={2} />
              ) : (
                <Menu className="w-6 h-6" strokeWidth={2} />
              )}
            </button>

            {/* 데스크톱: 사이드바 토글 버튼 */}
            <button
              type="button"
              onClick={toggleSidebar}
              className="hidden lg:flex p-2 rounded-token-sm text-text-sub hover:text-primary-500 hover:bg-bg-surface/50 transition-colors"
              aria-label={isSidebarOpen ? "사이드바 닫기" : "사이드바 열기"}
            >
              {isSidebarOpen ? (
                <PanelLeftClose className="w-5 h-5" strokeWidth={2} />
              ) : (
                <PanelLeftOpen className="w-5 h-5" strokeWidth={2} />
              )}
            </button>

            {/* 오른쪽: Welcome + 로그인/로그아웃 */}
            <div className="flex items-center gap-3 ml-auto">
              {isLogin && (
                <Link
                  to="/profile"
                  className="text-sm text-text-sub hover:text-primary-500 transition-colors"
                >
                  Welcome,{" "}
                  <span className="font-bold text-primary-400">
                    {loginState?.name || loginState?.email}님
                  </span>
                </Link>
              )}
              {!isLogin ? (
                <Link
                  to="/member/login"
                  className="p-2 rounded-token-sm text-primary-500 hover:text-primary-400 hover:bg-bg-surface/50 transition-colors"
                  aria-label="로그인"
                >
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
