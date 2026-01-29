import LoginComponent from "../../components/member/LoginComponent";
import BasicMenu from "../../components/menu/BasicMenu";

const LoginPage = () => {
  return (
    <div className="w-full bg-baseBg min-h-screen">
      <BasicMenu />
      <div className="ui-container py-12 lg:py-16 flex justify-center items-center">
        <div className="w-full max-w-lg">
          <LoginComponent />
        </div>
      </div>
    </div>
  );
};

export default LoginPage;