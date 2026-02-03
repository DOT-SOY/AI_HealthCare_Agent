import { Outlet } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";

const RankingIndex = () => {
  return (
    <BasicLayout>
      <Outlet />
    </BasicLayout>
  );
};

export default RankingIndex;
