import { Outlet } from "react-router-dom";
import ShopLayout from "../../components/layout/ShopLayout";

const ShopIndex = () => {
  return (
    <ShopLayout>
      <Outlet />
    </ShopLayout>
  );
};

export default ShopIndex;
