import BasicMenu from "../menu/BasicMenu";

const BasicLayout = ({ children }) => {
  return (
    <>
      <BasicMenu />
      <div className="lg:ml-44 app-main">
        <main>
          {children}
        </main>
      </div>
    </>
  );
};

export default BasicLayout;
