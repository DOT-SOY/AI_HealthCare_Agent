import { Outlet } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";

const RankingIndex = () => {
  return (
    <BasicLayout>
      <div className="w-full bg-baseBg min-h-screen">
        <div className="ui-container py-12 lg:py-16">
          <Outlet />
        </div>
      </div>
    </BasicLayout>
  );
};

export default RankingIndex;
