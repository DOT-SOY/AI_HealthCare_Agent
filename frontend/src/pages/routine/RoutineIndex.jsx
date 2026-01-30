import { Outlet } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";

const RoutineIndex = () => {
  return (
    <BasicLayout>
      <Outlet />
    </BasicLayout>
  );
};

export default RoutineIndex;
