import { Outlet } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";

const ProfileIndex = () => {
  return (
    <BasicLayout>
      <Outlet />
    </BasicLayout>
  );
};

export default ProfileIndex;
