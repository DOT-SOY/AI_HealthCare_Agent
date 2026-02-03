import { useEffect } from "react";
import { Link, Outlet, useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import BasicLayout from "../../components/layout/BasicLayout";

const AdminPage = () => {
  const loginState = useSelector((state) => state.loginSlice);
  const navigate = useNavigate();

  useEffect(() => {
    if (!loginState.roleNames || !loginState.roleNames.includes("ADMIN")) {
      alert("접근 권한이 없습니다 (관리자 전용)");
      navigate("/", { replace: true });
    }
  }, [loginState, navigate]);

  if (!loginState.roleNames?.includes("ADMIN")) {
    return null;
  }

  return (
    <BasicLayout>
      <div className="w-full max-w-5xl mx-auto py-6">
        <nav className="flex gap-4 mb-6 border-b pb-4">
          <Link to="/admin/orders" className="text-gray-600 hover:text-gray-900">
            주문 관리
          </Link>
        </nav>
        <Outlet />
      </div>
    </BasicLayout>
  );
};

export default AdminPage;