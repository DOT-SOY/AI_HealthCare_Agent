import BasicMenu from "../menu/BasicMenu";

const BasicLayout = ({ children }) => {
  return (
    <>
      <BasicMenu />
      <div className="lg:ml-64 min-w-0">
        <main className="w-full">
          {children}
        </main>
      </div>
    </>
  );
};

export default BasicLayout;
