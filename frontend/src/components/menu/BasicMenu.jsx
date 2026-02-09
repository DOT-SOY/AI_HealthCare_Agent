import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useSelector } from "react-redux";
import { useTheme } from "../../contexts/ThemeContext";

// 간단한 SVG 아이콘 컴포넌트들
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

const SunIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
  </svg>
);

const MoonIcon = ({ className }) => (
  <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
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

const BasicMenu = ({ isSidebarOpen = true }) => {
  const location = useLocation();
  const { theme, setTheme } = useTheme();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [shopDropdownOpen, setShopDropdownOpen] = useState(false);
  const [shopMobileOpen, setShopMobileOpen] = useState(false);
  const loginState = useSelector((state) => state.loginSlice);
  const isLogin = !!loginState?.email;
  const isAdmin =
    Array.isArray(loginState?.roleNames) &&
    loginState.roleNames.some((r) => r === "ADMIN" || r === "ROLE_ADMIN");

  const toggleMobileMenu = () => setIsMobileMenuOpen(!isMobileMenuOpen);
  const closeMobileMenu = () => {
    setIsMobileMenuOpen(false);
    setShopMobileOpen(false);
  };

  const menuItems = [
    { icon: HomeIcon, label: "Home", path: "/" },
    { icon: ClockIcon, label: "루틴", path: "/routine" },
    { icon: FileTextIcon, label: "기록", path: "/record" },
    { icon: UtensilsIcon, label: "식사", path: "/meal" },
    {
      icon: ShoppingBagIcon,
      label: "쇼핑",
      path: "/shop",
        children: [
        { label: "상품 목록", path: "/shop/list" },
        { label: "내 주문 내역", path: "/shop/orders" },
      ],
    },
    { icon: StarIcon, label: "랭킹", path: "/ranking" },
    { icon: UserIcon, label: "프로필", path: "/profile" },
  ];

  const isActive = (path) => {
    if (path === "/") return location.pathname === path;
    return location.pathname.startsWith(path);
  };

  const linkBase =
    "flex items-center gap-3 px-4 py-3 rounded-token transition-all duration-200 origin-left group ";
  const linkInactive =
    "text-text-main hover:bg-gray-100 hover:scale-105";
  const linkActive =
    "text-primary-500 font-bold scale-105";

  const getMenuClass = (path) => {
    return linkBase + (isActive(path) ? linkActive : linkInactive);
  };

  const subLinkClass = (path) => {
    const base = "block px-3 py-2 text-sm rounded-token transition-all duration-200 ";
    return isActive(path)
      ? base + "text-primary-500 font-bold"
      : base + "text-text-sub hover:bg-gray-100 hover:text-text-main hover:scale-[1.02]";
  };

  return (
    <>
      {/* 데스크톱 사이드바 — 쇼핑몰 리스트와 동일한 다크·네온 그린 스타일 */}
      <aside className={`hidden lg:flex fixed left-0 top-0 h-full w-64 bg-bg-card border-r border-border-default flex-col z-50 transition-transform duration-300 ease-[cubic-bezier(0.25,1,0.5,1)] ${isSidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="p-5 border-b border-border-default">
          <Link to="/" className="flex items-center gap-3">
            <img src="/logo.png" alt="ALGORHYGYM" className="w-10 h-10 flex-shrink-0 object-contain" />
            <span className="font-display text-xl tracking-tight text-text-main uppercase">
              ALGORHYGYM
            </span>
          </Link>
        </div>

        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {menuItems.map((item) => {
            const Icon = item.icon;
            if (item.children) {
              return (
                <div
                  key={item.path}
                  onMouseEnter={() => setShopDropdownOpen(true)}
                  onMouseLeave={() => setShopDropdownOpen(false)}
                >
                  <Link to={item.path} className={getMenuClass(item.path)}>
                    <Icon
                      className={`w-5 h-5 flex-shrink-0 transition-transform duration-200 ${isActive(item.path) ? "scale-110" : "group-hover:scale-110"}`}
                      strokeWidth={2}
                    />
                    <span>{item.label}</span>
                  </Link>
                  {/* 아코디언: grid-template-rows로 높이 애니메이션 */}
                  <div
                    className="grid transition-[grid-template-rows] duration-300 ease-out"
                    style={{ gridTemplateRows: shopDropdownOpen ? "1fr" : "0fr" }}
                  >
                    <div className="min-h-0 overflow-hidden">
                      <div className="pl-4 mt-1 space-y-1 border-l-2 border-border-default ml-4">
                        {item.children.map((child) => (
                          <Link
                            key={child.path}
                            to={child.path}
                            className={subLinkClass(child.path)}
                          >
                            {child.label}
                          </Link>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              );
            }
            return (
              <Link key={item.path} to={item.path} className={getMenuClass(item.path)}>
                <Icon
                  className={`w-5 h-5 flex-shrink-0 transition-transform duration-200 ${isActive(item.path) ? "scale-110" : "group-hover:scale-110"}`}
                  strokeWidth={2}
                />
                <span>{item.label}</span>
              </Link>
            );
          })}

          {/* 프로필 아래 로그인 (비로그인 시만) */}
          {!isLogin && (
            <Link
              to="/member/login"
              className={getMenuClass("/member/login")}
              onClick={closeMobileMenu}
            >
              <LockClosedIcon className="w-5 h-5 flex-shrink-0 transition-transform duration-200 group-hover:scale-110" />
              <span>로그인</span>
            </Link>
          )}

          {isLogin && isAdmin && (
            <Link to="/admin" className={getMenuClass("/admin")} onClick={closeMobileMenu}>
              <AdminIcon className="w-5 h-5 flex-shrink-0 transition-transform duration-200 group-hover:scale-110" />
              <span>관리자</span>
            </Link>
          )}
        </nav>

        {/* 테마 토글 (데스크톱 사이드바) — 아이콘만 표시 */}
        <div className="p-3 border-t border-border-default">
          <div
            className="relative flex w-full rounded-full border border-border-default bg-bg-surface p-0.5"
            role="group"
            aria-label="테마 선택"
          >
            <span
              className="absolute top-0.5 bottom-0.5 w-[calc(50%-6px)] rounded-full bg-primary-500/20 transition-all duration-300 ease-[cubic-bezier(0.25,1,0.5,1)]"
              style={{ left: theme === "dark" ? "4px" : "calc(50% + 2px)" }}
              aria-hidden
            />
            <button
              type="button"
              onClick={() => setTheme("dark")}
              className="relative z-10 flex flex-1 items-center justify-center py-2.5 rounded-full text-sm font-medium transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
              aria-pressed={theme === "dark"}
              aria-label="다크 모드"
            >
              <MoonIcon
                className={`h-5 w-5 flex-shrink-0 transition-transform duration-200 ${
                  theme === "dark" ? "text-primary-500 scale-110" : "text-text-sub"
                }`}
              />
            </button>
            <button
              type="button"
              onClick={() => setTheme("light")}
              className="relative z-10 flex flex-1 items-center justify-center py-2.5 rounded-full text-sm font-medium transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
              aria-pressed={theme === "light"}
              aria-label="라이트 모드"
            >
              <SunIcon
                className={`h-5 w-5 flex-shrink-0 transition-transform duration-200 ${
                  theme === "light" ? "text-primary-500 scale-110" : "text-text-sub"
                }`}
              />
            </button>
          </div>
        </div>
      </aside>

      {/* 모바일 햄버거 버튼 */}
      <button
        onClick={toggleMobileMenu}
        className="lg:hidden fixed top-4 left-4 z-50 p-2.5 bg-bg-card rounded-token border border-border-default shadow-card text-text-main hover:border-primary-500 hover:text-primary-500 transition-all duration-200"
        aria-label="메뉴 열기"
      >
        {isMobileMenuOpen ? (
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        ) : (
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        )}
      </button>

      {isMobileMenuOpen && (
        <div
          className="lg:hidden fixed inset-0 bg-bg-root/80 z-40"
          onClick={closeMobileMenu}
          aria-hidden
        />
      )}

      {/* 모바일 사이드바 */}
      <aside
        className={`lg:hidden fixed left-0 top-0 h-full w-64 bg-bg-card border-r border-border-default shadow-card z-50 transform transition-transform duration-300 ease-out-quart flex flex-col ${
          isMobileMenuOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="p-5 border-b border-border-default">
          <Link to="/" className="flex items-center gap-3" onClick={closeMobileMenu}>
            <img src="/logo.png" alt="ALGORHYGYM" className="w-10 h-10 flex-shrink-0 object-contain" />
            <span className="font-display text-xl tracking-tight text-text-main uppercase">
              ALGORHYGYM
            </span>
          </Link>
        </div>

        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {menuItems.map((item) => {
            const Icon = item.icon;
            if (item.children) {
              return (
                <div key={item.path}>
                  <button
                    type="button"
                    onClick={() => setShopMobileOpen((prev) => !prev)}
                    className={`w-full text-left ${getMenuClass(item.path)}`}
                  >
                    <Icon
                      className={`w-5 h-5 flex-shrink-0 transition-transform duration-200 ${isActive(item.path) ? "scale-110" : "group-hover:scale-110"}`}
                      strokeWidth={2}
                    />
                    <span>{item.label}</span>
                  </button>
                  {shopMobileOpen && (
                    <div className="pl-4 mt-1 space-y-1 border-l-2 border-border-default ml-4">
                      {item.children.map((child) => (
                        <Link
                          key={child.path}
                          to={child.path}
                          className={subLinkClass(child.path)}
                          onClick={closeMobileMenu}
                        >
                          {child.label}
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
              );
            }
            return (
              <Link
                key={item.path}
                to={item.path}
                className={getMenuClass(item.path)}
                onClick={closeMobileMenu}
              >
                <Icon
                  className={`w-5 h-5 flex-shrink-0 transition-transform duration-200 ${isActive(item.path) ? "scale-110" : "group-hover:scale-110"}`}
                  strokeWidth={2}
                />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* 테마 토글 (모바일 사이드바) — 아이콘만 표시 */}
        <div className="p-3 border-t border-border-default">
          <div
            className="relative flex w-full rounded-full border border-border-default bg-bg-surface p-0.5"
            role="group"
            aria-label="테마 선택"
          >
            <span
              className="absolute top-0.5 bottom-0.5 w-[calc(50%-6px)] rounded-full bg-primary-500/20 transition-all duration-300 ease-[cubic-bezier(0.25,1,0.5,1)]"
              style={{ left: theme === "dark" ? "4px" : "calc(50% + 2px)" }}
              aria-hidden
            />
            <button
              type="button"
              onClick={() => setTheme("dark")}
              className="relative z-10 flex flex-1 items-center justify-center py-2.5 rounded-full text-sm font-medium transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
              aria-pressed={theme === "dark"}
              aria-label="다크 모드"
            >
              <MoonIcon
                className={`h-5 w-5 flex-shrink-0 transition-transform duration-200 ${
                  theme === "dark" ? "text-primary-500 scale-110" : "text-text-sub"
                }`}
              />
            </button>
            <button
              type="button"
              onClick={() => setTheme("light")}
              className="relative z-10 flex flex-1 items-center justify-center py-2.5 rounded-full text-sm font-medium transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
              aria-pressed={theme === "light"}
              aria-label="라이트 모드"
            >
              <SunIcon
                className={`h-5 w-5 flex-shrink-0 transition-transform duration-200 ${
                  theme === "light" ? "text-primary-500 scale-110" : "text-text-sub"
                }`}
              />
            </button>
          </div>
        </div>
      </aside>
    </>
  );
};

export default BasicMenu;
