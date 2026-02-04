import { useEffect } from "react";
import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";
import AdminComponent from "../../components/admin/AdminComponent";

const AdminPage = () => {
  const loginState = useSelector((state) => state.loginSlice);
  const navigate = useNavigate();

  useEffect(() => {
    if (!loginState.roleNames || !loginState.roleNames.includes("ADMIN")) {
      alert("접근 권한이 없습니다 (관리자 전용)");
      navigate("/", { replace: true });
    }
  }, [loginState, navigate]);

  return (
    <BasicLayout>
      <div className="min-h-[600px]">
        <AdminComponent />
      </div>
    </BasicLayout>
  );
};

export default AdminPage;