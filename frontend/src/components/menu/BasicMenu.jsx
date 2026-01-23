import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { Home, Clock, FileText, UtensilsCrossed, ShoppingBag, Star, User } from "lucide-react";

const BasicMenu = () => {
  const location = useLocation();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const toggleMobileMenu = () => setIsMobileMenuOpen(!isMobileMenuOpen);
  const closeMobileMenu = () => setIsMobileMenuOpen(false);

  const menuItems = [
    { icon: Home, label: "메인", path: "/" },
    { icon: Clock, label: "루틴", path: "/routine" },
    { icon: FileText, label: "기록", path: "/record" },
    { icon: UtensilsCrossed, label: "식사", path: "/meal" },
    { icon: ShoppingBag, label: "쇼핑", path: "/shop" },
    { icon: Star, label: "랭킹", path: "/ranking" },
    { icon: User, label: "프로필", path: "/profile" },
  ];

  const isActive = (path) => {
    if (path === "/") {
      return location.pathname === path;
    }
    return location.pathname.startsWith(path);
  };

  const getMenuClass = (path) => {
    const baseClass = "flex items-center gap-3 px-4 py-3 rounded-lg transition-colors duration-200 ";
    return isActive(path)
      ? baseClass + "bg-blue-600 text-white"
      : baseClass + "text-gray-700 hover:bg-gray-100";
  };

  return (
    <>
      {/* 데스크톱 사이드바 */}
      <aside className="hidden lg:flex fixed left-0 top-0 h-full w-64 bg-white border-r border-gray-200 flex-col shadow-lg z-50">
        {/* 로고 */}
        <div className="p-6 border-b border-gray-200">
          <Link to="/" className="flex items-center gap-2">
            <div className="w-10 h-10 bg-blue-600 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold text-lg">H</span>
            </div>
            <span className="text-xl font-semibold text-gray-900">
              Healthcare
            </span>
          </Link>
        </div>

        {/* 메뉴 아이템 */}
        <nav className="flex-1 p-4 space-y-2">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={getMenuClass(item.path)}
              >
                <Icon className="w-5 h-5" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </aside>

      {/* 모바일 햄버거 버튼 */}
      <button
        onClick={toggleMobileMenu}
        className="lg:hidden fixed top-4 left-4 z-50 p-2 bg-white rounded-lg shadow-md border border-gray-200"
        aria-label="메뉴 열기"
      >
        {isMobileMenuOpen ? (
          <svg
            className="w-6 h-6 text-gray-700"
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
            className="w-6 h-6 text-gray-700"
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
        className={`lg:hidden fixed left-0 top-0 h-full w-64 bg-white shadow-xl z-50 transform transition-transform duration-300 ease-in-out ${
          isMobileMenuOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        {/* 로고 */}
        <div className="p-6 border-b border-gray-200">
          <Link to="/" className="flex items-center gap-2" onClick={closeMobileMenu}>
            <div className="w-10 h-10 bg-blue-600 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold text-lg">H</span>
            </div>
            <span className="text-xl font-semibold text-gray-900">
              Healthcare
            </span>
          </Link>
        </div>

        {/* 메뉴 아이템 */}
        <nav className="flex-1 p-4 space-y-2">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={getMenuClass(item.path)}
                onClick={closeMobileMenu}
              >
                <Icon className="w-5 h-5" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </aside>
    </>
  );
};

export default BasicMenu;
