import React, { useState } from "react"; // useState import 추가
import "../../styles/Profile.css";
import BasicLayout from "../../components/layout/BasicLayout";
import { Home, User, Moon, Sun } from "lucide-react"; // 아이콘 추가
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
} from "recharts";

const chartData = [
  { name: "1회차", fatRate: 4.5, muscle: 2.2, weight: 2.0 },
  { name: "2회차", fatRate: 2.5, muscle: 4.5, weight: 2.0 },
  { name: "3회차", fatRate: 3.5, muscle: 1.8, weight: 3.0 },
  { name: "4회차", fatRate: 4.5, muscle: 2.8, weight: 5.0 },
];

const ProfileIndex = () => {
  // ✅ 다크 모드 상태 관리
  const [isDark, setIsDark] = useState(false);

  // 토글 함수
  const toggleDarkMode = () => setIsDark((prev) => !prev);

  return (
    <BasicLayout>
      {/* data-theme 속성을 통해 CSS 변수를 제어합니다 */}
      <div
        className="dashboard-container"
        data-theme={isDark ? "dark" : "light"}
      >
        {/* --- 헤더 --- */}
        <header className="dashboard-header">
          <Home className="icon-home" size={24} />

          <div className="header-right">
            {/* ✅ 다크 모드 토글 버튼 */}
            <button className="btn-toggle-theme" onClick={toggleDarkMode}>
              {isDark ? <Sun size={20} /> : <Moon size={20} />}
            </button>
            <button className="btn-logout">로그아웃</button>
          </div>
        </header>

        {/* --- 메인 그리드 레이아웃 --- */}
        <div className="dashboard-main">
          {/* ============ 왼쪽 사이드바 ============ */}
          <aside className="left-sidebar">
            <div className="info-card">
              <div className="card-header">
                <h2>회원정보</h2>
                <button className="btn-edit">
                  <User size={12} /> 정보 수정
                </button>
              </div>
              <div className="card-content profile-details">
                <div className="row">
                  <span className="name">김길동</span>
                  <span className="gender-icon">♂ 남성</span>
                </div>
                <div className="row date">2004-01-23 (20세)</div>
                <div className="row stats">
                  <span>176.5 cm</span> <span>/</span> <span>76.0 kg</span>
                </div>
              </div>
            </div>

            <div className="info-card">
              <h3 className="section-title">체성분 분석</h3>
              <div className="data-list">
                <DataRow label="체수분(L)" value="27.4 %" />
                <DataRow label="단백질(kg)" value="7.1 %" />
                <DataRow label="무기질(kg)" value="2.64 %" />
                <DataRow label="체지방(kg)" value="22.0 %" />
              </div>
            </div>

            <div className="info-card">
              <h3 className="section-title">체중조절</h3>
              <div className="data-list">
                <DataRow label="적정체중" value="72.0 kg" />
                <DataRow label="체중조절" value="- 7.4 kg" />
                <DataRow label="지방조절" value="- 10.1 kg" />
                <DataRow label="근육조절" value="+ 2.7 kg" />
              </div>
            </div>
          </aside>

          {/* ============ 오른쪽 차트 영역 ============ */}
          <main className="right-content">
            <div className="badge-row">
              <span className="lime-badge">인바디 자동분석</span>
            </div>

            <div className="charts-container">
              {/* 차트 컴포넌트에 isDark props를 전달하여 스타일을 동적으로 변경합니다 */}
              <ChartRow
                title="체지방률"
                value="86.0%"
                chartTitle="차트 제목"
                dataKey="fatRate"
                strokeColor="#4A90E2"
                isDark={isDark}
              />
              <ChartRow
                title="골격근량"
                value="86.0%"
                chartTitle="차트 제목"
                dataKey="muscle"
                strokeColor="#D0021B"
                isDark={isDark}
              />
              <ChartRow
                title="체중"
                value="86.0%"
                chartTitle="차트 제목"
                dataKey="weight"
                strokeColor="#7ED321"
                isDark={isDark}
              />
            </div>
          </main>
        </div>
      </div>
    </BasicLayout>
  );
};

// ---------------- 헬퍼 컴포넌트 ----------------

function DataRow({ label, value }) {
  return (
    <div className="data-row">
      <span className="label">{label}</span>
      <span className="value">{value}</span>
    </div>
  );
}

function ChartRow({ title, value, chartTitle, dataKey, strokeColor, isDark }) {
  return (
    <div className="chart-layout-row">
      <div className="grey-info-box">
        <div className="info-title">{title}</div>
        <div className="info-value">{value}</div>
      </div>

      <div className="chart-area">
        <div className="chart-main-title">{chartTitle}</div>
        <div style={{ width: "100%", height: "160px" }}>
          {/* Chart에도 다크모드 정보 전달 */}
          <SimpleLineChart
            dataKey={dataKey}
            stroke={strokeColor}
            isDark={isDark}
          />
        </div>
      </div>
    </div>
  );
}

function SimpleLineChart({ dataKey, stroke, isDark }) {
  // 다크 모드일 때 차트 축/격자 색상 변경
  const axisColor = isDark ? "#aaaaaa" : "#666";
  const gridColor = isDark ? "#444" : "#e0e0e0";

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart
        data={chartData}
        margin={{ top: 5, right: 20, left: -20, bottom: 5 }}
      >
        <CartesianGrid vertical={false} stroke={gridColor} />
        <XAxis
          dataKey="name"
          tickLine={false}
          axisLine={{ stroke: gridColor }}
          tick={{ fontSize: 12, fill: axisColor }}
          stroke={gridColor}
        />
        <YAxis
          hide={false}
          tick={{ fontSize: 12, fill: axisColor }}
          axisLine={false}
          tickLine={false}
          domain={[0, 5]}
          tickCount={6}
        />
        <Tooltip
          contentStyle={{
            backgroundColor: isDark ? "#333" : "#fff",
            borderColor: isDark ? "#555" : "#ccc",
            color: isDark ? "#fff" : "#000",
          }}
        />
        <Line
          type="linear"
          dataKey={dataKey}
          stroke={stroke}
          strokeWidth={2}
          dot={false}
          name="계열 1"
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

export default ProfileIndex;
