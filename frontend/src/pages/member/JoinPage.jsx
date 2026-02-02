import JoinComponent from "../../components/member/JoinComponent";
import BasicMenu from "../../components/menu/BasicMenu";

const JoinPage = () => {
  return (
    <div className="page-root">
      <BasicMenu />
      <div className="page-container flex justify-center items-center">
        <div className="w-full max-w-lg">
          <JoinComponent />
        </div>
      </div>
    </div>
  );
};

export default JoinPage;