import { Outlet } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";

const RecordIndex = () => {
  return (
    <BasicLayout>
      <Outlet />
    </BasicLayout>
  );
};

export default RecordIndex;
