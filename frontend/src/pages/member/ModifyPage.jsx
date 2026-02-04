import ModifyComponent from "../../components/member/ModifyComponent";
import BasicMenu from "../../components/menu/BasicMenu";

const ModfyPage = () => {
  return (
    <div data-theme="dark" className="page-root">
      <BasicMenu />
      <div className="page-container flex justify-center items-center">
        <div className="w-full max-w-lg">
          <ModifyComponent />
        </div>
      </div>
    </div>
  );
};

export default ModfyPage;