import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { logout } from "../../slices/loginSlice";

// 로고용 번개 아이콘 (라임 그린 박스 안)
const LightningIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
  </svg>
);

const HomeIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
  </svg>
);

const ClockIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
);

const FileTextIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
  </svg>
);

const UtensilsIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
  </svg>
);

const ShoppingBagIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
  </svg>
);

const StarIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
  </svg>
);

const UserIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
  </svg>
);

const LockClosedIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M8 11V7a4 4 0 118 0v4M7 11h10a1 1 0 011 1v7a1 1 0 01-1 1H7a1 1 0 01-1-1v-7a1 1 0 011-1z"
    />
  </svg>
);

const LockOpenIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M15 7a3 3 0 00-6 0v1M7 11h10a1 1 0 011 1v7a1 1 0 01-1 1H7a1 1 0 01-1-1v-7a1 1 0 011-1zM15 7a3 3 0 015 3v1"
    />
  </svg>
);

const AdminIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
    />
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
  </svg>
);

const BasicMenu = () => {
  const location = useLocation();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const dispatch = useDispatch();
  const loginState = useSelector((state) => state.loginSlice);
  const isLogin = !!loginState?.email;
  const isAdmin =
    Array.isArray(loginState?.roleNames) &&
    loginState.roleNames.some((r) => r === "ADMIN" || r === "ROLE_ADMIN");

  const toggleMobileMenu = () => setIsMobileMenuOpen(!isMobileMenuOpen);
  const closeMobileMenu = () => setIsMobileMenuOpen(false);
  const handleClickLogout = async () => {
    await dispatch(logout());
    closeMobileMenu();
  };

  const menuItems = [
    { icon: HomeIcon, label: "Home", path: "/" },
    { icon: ClockIcon, label: "루틴", path: "/routine" },
    { icon: FileTextIcon, label: "기록", path: "/record" },
    { icon: UtensilsIcon, label: "식사", path: "/meal" },
    { icon: ShoppingBagIcon, label: "쇼핑", path: "/shop" },
    { icon: StarIcon, label: "랭킹", path: "/ranking" },
    { icon: UserIcon, label: "프로필", path: "/profile" },
  ];

  const isActive = (path) => {
    if (path === "/") {
      return location.pathname === path;
    }
    return location.pathname.startsWith(path);
  };

  const getMenuClass = (path) => {
    return isActive(path) ? "app-menu-item active" : "app-menu-item";
  };

  return (
    <>
      {/* 데스크톱 사이드바 */}
      <aside className="hidden lg:flex fixed left-0 top-0 h-full w-44 flex-col shadow-lg z-50 app-sidebar">
        {/* 로고: 라임 그린 둥근 박스 + 번개 아이콘만 */}
        <div className="p-6 app-sidebar-header flex justify-center">
          <Link to="/" className="app-sidebar-logo-box">
            <LightningIcon className="w-6 h-6 text-white" />
          </Link>
        </div>

        {/* 메뉴 아이템: 아이콘 위, 텍스트 아래 세로 배치 */}
        <nav className="flex-1 p-4 flex flex-col items-center gap-1">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={getMenuClass(item.path)}
              >
                <Icon className="w-6 h-6 shrink-0" />
                <span className="text-xs whitespace-nowrap">{item.label}</span>
              </Link>
            );
          })}

          {/* 프로필 아래에 로그인/로그아웃 (잠금 아이콘) */}
          {!isLogin ? (
            <Link
              to="/member/login"
              className={getMenuClass("/member/login")}
              onClick={closeMobileMenu}
            >
              <LockClosedIcon className="w-6 h-6 shrink-0" />
              <span className="text-xs whitespace-nowrap">로그인</span>
            </Link>
          ) : (
            <button
              type="button"
              onClick={handleClickLogout}
              className={`${getMenuClass("/member/logout")} border-none bg-transparent cursor-pointer`}
            >
              <LockOpenIcon className="w-6 h-6 shrink-0" />
              <span className="text-xs whitespace-nowrap">로그아웃</span>
            </button>
          )}

          {isLogin && isAdmin && (
            <Link to="/admin" className={getMenuClass("/admin")} onClick={closeMobileMenu}>
              <AdminIcon className="w-6 h-6 shrink-0" />
              <span className="text-xs whitespace-nowrap">관리자</span>
            </Link>
          )}
        </nav>

        {/* 하단: 로그인 시 이름만 표시 */}
        <div className="mt-auto p-4 app-sidebar-footer flex flex-col items-center gap-2">
          {isLogin && (
            <div className="text-center">
              <div className="app-user-welcome">Welcome</div>
              <div className="app-user-name">
                {(loginState?.name || loginState?.email)
                  ? `${loginState?.name || loginState?.email}님`
                  : ""}
              </div>
            </div>
          )}
        </div>
      </aside>

      {/* 모바일 햄버거 버튼 */}
      <button
        onClick={toggleMobileMenu}
        className="lg:hidden fixed top-4 left-4 z-50 p-2 rounded-lg shadow-md border border-[var(--color-sidebar-border)] app-sidebar"
        aria-label="메뉴 열기"
      >
        {isMobileMenuOpen ? (
          <svg
            className="w-6 h-6 text-[var(--color-text)]"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        ) : (
          <svg
            className="w-6 h-6 text-[var(--color-text)]"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M4 6h16M4 12h16M4 18h16"
            />
          </svg>
        )}
      </button>

      {/* 모바일 사이드바 오버레이 */}
      {isMobileMenuOpen && (
        <div
          className="lg:hidden fixed inset-0 bg-black bg-opacity-50 z-40"
          onClick={closeMobileMenu}
        />
      )}

      {/* 모바일 사이드바 */}
      <aside
        className={`lg:hidden fixed left-0 top-0 h-full w-44 shadow-xl z-50 transform transition-transform duration-300 ease-in-out flex flex-col app-sidebar app-sidebar-mobile ${
          isMobileMenuOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        {/* 로고: 라임 그린 둥근 박스 + 번개 아이콘만 */}
        <div className="p-6 app-sidebar-header flex justify-center">
          <Link to="/" className="app-sidebar-logo-box" onClick={closeMobileMenu}>
            <LightningIcon className="w-6 h-6 text-white" />
          </Link>
        </div>

        {/* 메뉴 아이템: 아이콘 위, 텍스트 아래 세로 배치 */}
        <nav className="flex-1 p-4 flex flex-col items-center gap-1">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={getMenuClass(item.path)}
                onClick={closeMobileMenu}
              >
                <Icon className="w-6 h-6 shrink-0" />
                <span className="text-xs whitespace-nowrap">{item.label}</span>
              </Link>
            );
          })}

          {/* 프로필 아래에 로그인/로그아웃 (잠금 아이콘, 모바일) */}
          {!isLogin ? (
            <Link
              to="/member/login"
              className={getMenuClass("/member/login")}
              onClick={closeMobileMenu}
            >
              <LockClosedIcon className="w-6 h-6 shrink-0" />
              <span className="text-xs whitespace-nowrap">로그인</span>
            </Link>
          ) : (
            <button
              type="button"
              onClick={() => {
                handleClickLogout();
                closeMobileMenu();
              }}
              className={`${getMenuClass("/member/logout")} border-none bg-transparent cursor-pointer`}
            >
              <LockOpenIcon className="w-6 h-6 shrink-0" />
              <span className="text-xs whitespace-nowrap">로그아웃</span>
            </button>
          )}

          {isLogin && isAdmin && (
            <Link to="/admin" className={getMenuClass("/admin")} onClick={closeMobileMenu}>
              <AdminIcon className="w-6 h-6 shrink-0" />
              <span className="text-xs whitespace-nowrap">관리자</span>
            </Link>
          )}
        </nav>

        {/* 하단: 로그인 시 이름만 표시 (모바일) */}
        <div className="mt-auto p-4 app-sidebar-footer flex flex-col items-center gap-2">
          {isLogin && (
            <div className="text-center">
              <div className="app-user-welcome">Welcome</div>
              <div className="app-user-name">
                {(loginState?.name || loginState?.email)
                  ? `${loginState?.name || loginState?.email}님`
                  : ""}
              </div>
            </div>
          )}
        </div>
      </aside>
    </>
  );
};

export default BasicMenu;
