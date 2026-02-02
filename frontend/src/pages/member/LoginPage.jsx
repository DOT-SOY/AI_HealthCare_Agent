import LoginComponent from "../../components/member/LoginComponent";
import BasicMenu from "../../components/menu/BasicMenu";

const LoginPage = () => {
  return (
    <div className="page-root">
      <BasicMenu />
      <div className="page-container flex justify-center items-center">
        <div className="w-full max-w-lg">
          <LoginComponent />
        </div>
      </div>
    </div>
  );
};

export default LoginPage;