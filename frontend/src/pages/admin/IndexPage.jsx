import { Outlet } from "react-router-dom";
import BasicLayout from "../../layouts/BasicLayout";

const IndexPage = () => {
  return (
    <BasicLayout>
        <Outlet />
    </BasicLayout>
  );
};

export default IndexPage;