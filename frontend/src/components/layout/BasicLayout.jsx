import BasicMenu from "../menu/BasicMenu";

const BasicLayout = ({ children }) => {
  return (
    <>
      <BasicMenu />
      <div className="lg:ml-64">
        <main>
          {children}
        </main>
      </div>
    </>
  );
};

export default BasicLayout;
