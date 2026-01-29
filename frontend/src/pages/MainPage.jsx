import BasicLayout from "../components/layout/BasicLayout";

function Main() {
  return (
    <BasicLayout>
      <div className="p-8 flex flex-col items-start gap-6">
        {/* 좌측 상단 ALGORHYGYM 로고 - CSS 배경으로 처리 */}
        <div className="main-logo" aria-label="ALGORHYGYM 로고" />

        <h1 style={{ textAlign: "left" }}>메인 페이지</h1>
      </div>
    </BasicLayout>
  );
}

export default Main;
