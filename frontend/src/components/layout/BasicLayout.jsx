import BasicMenu from "../menu/BasicMenu";
import AIChatOverlay from '../../pages/AIChatOverlay';

const BasicLayout = ({ children }) => {
  return (
    <>
      <BasicMenu />
      <div className="lg:ml-44 app-main">
        <main>
      <div className="lg:ml-64">
        <main className="pt-4">
          {children}
        </main>
      </div>
        </main>
      </div>
      <AIChatOverlay />
    </>
  );
};

export default BasicLayout;
