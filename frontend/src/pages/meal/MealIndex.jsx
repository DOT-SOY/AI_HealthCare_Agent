import { Outlet } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";

const MealIndex = () => {
  return (
    <BasicLayout>
      <Outlet />
    </BasicLayout>
  );
};

export default MealIndex;
