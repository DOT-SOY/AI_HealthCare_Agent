import { Outlet } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";

const MealIndex = () => {
  return (
    <BasicLayout>
      <div className="w-full bg-[#121212] text-white min-h-screen">
        <div className="ui-container py-8 lg:py-10">
          <Outlet />
        </div>
      </div>
    </BasicLayout>
  );
};

export default MealIndex;
