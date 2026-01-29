import { useEffect } from "react";
import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
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
    <div className="w-full bg-baseBg min-h-screen py-6 lg:py-8">
      <div className="ui-container">
        <div className="p-6 lg:p-8 min-h-[600px]">
          <AdminComponent />
        </div>
      </div>
    </div>
  );
};

export default AdminPage;