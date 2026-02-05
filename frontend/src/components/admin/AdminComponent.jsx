import { Link } from "react-router-dom";

/**
 * Admin 메인 진입 컴포넌트
 * - 기존 MainPage.jsx에서 import하고 있으나 컴포넌트가 누락되어 빌드가 깨지는 문제를 방지합니다.
 * - 실제 관리자 기능은 각 admin 페이지로 이동하도록 최소 UI만 제공합니다.
 */
const AdminComponent = () => {
  return (
    <div className="w-full rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-xl font-bold text-gray-900 mb-2">관리자</h2>
      <p className="text-sm text-gray-600 mb-5">
        관리자 기능 페이지로 이동하세요.
      </p>

      <div className="flex flex-col gap-2">
        <Link
          to="/shop/admin/create"
          className="inline-flex items-center justify-center rounded-lg bg-black px-4 py-2 text-sm font-semibold text-white hover:bg-gray-900"
        >
          상품 등록
        </Link>
        <Link
          to="/shop/admin/edit"
          className="inline-flex items-center justify-center rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-semibold text-gray-900 hover:bg-gray-50"
        >
          상품 수정
        </Link>
      </div>
    </div>
  );
};

export default AdminComponent;



