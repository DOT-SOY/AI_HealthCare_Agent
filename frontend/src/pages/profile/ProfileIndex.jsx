import React, { useState, useEffect } from "react";
import "../../styles/Profile.css";
import BasicLayout from "../../components/layout/BasicLayout";
import { Home, User, Moon, Sun } from "lucide-react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
} from "recharts";

// ✅ API 함수 import (경로 확인 필요)
import { getBodyInfoHistory } from "../../services/bodyInfoApi";

const ProfileIndex = () => {
  const [isDark, setIsDark] = useState(false);

  // ✅ 서버에서 받아온 데이터를 저장할 State
  const [historyData, setHistoryData] = useState([]); // 차트 데이터
  const [latestInfo, setLatestInfo] = useState(null); // 최신 정보(텍스트 표시용)
  const [isLoading, setIsLoading] = useState(true); // 로딩 상태
  const [error, setError] = useState(null); // 에러 상태

  // 임시 회원 ID (로그인 기능 연동 시 변경 필요)
  const memberId = 1;

  // ✅ 데이터 로딩 useEffect
  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      setError(null);
      try {
        console.log(
          `[ProfileIndex] 체성분 정보 조회 시작 - memberId: ${memberId}`,
        );
        const response = await getBodyInfoHistory(memberId);
        console.log(`[ProfileIndex] 응답 데이터:`, response);

        if (response && Array.isArray(response) && response.length > 0) {
          // 1. 차트용 데이터 가공 (날짜순 정렬 후)
          const sortedResponse = [...response].sort((a, b) => {
            const dateA = new Date(a.measuredTime);
            const dateB = new Date(b.measuredTime);
            return dateA - dateB;
          });

          const formattedChartData = sortedResponse.map((item, index) => ({
            name: `${index + 1}회차`, // X축 라벨
            fatRate: item.bodyFatPercent || 0,
            muscle: item.skeletalMuscleMass || 0,
            weight: item.weight || 0,
            date: item.measuredTime, // 툴팁용 날짜
          }));
          setHistoryData(formattedChartData);

          // 2. 가장 최신 데이터 (마지막 요소)
          const recent = sortedResponse[sortedResponse.length - 1];
          setLatestInfo(recent);
        } else {
          console.log("[ProfileIndex] 조회된 데이터가 없습니다.");
          setHistoryData([]);
          setLatestInfo(null);
        }
      } catch (error) {
        console.error("[ProfileIndex] 데이터 로드 중 오류 발생:", error);
        setError(error.message || "데이터를 불러오는데 실패했습니다.");
        setHistoryData([]);
        setLatestInfo(null);
      } finally {
        setIsLoading(false);
      }
    };

    fetchData();
  }, [memberId]);

  const toggleDarkMode = () => setIsDark((prev) => !prev);

  // 데이터가 없을 경우를 대비한 기본 객체 (백엔드 DTO 구조에 맞춤)
  const info = latestInfo || {
    id: null,
    measuredTime: null,
    weight: 0,
    skeletalMuscleMass: 0,
    bodyFatPercent: 0,
    bodyWater: 0,
    protein: 0,
    minerals: 0,
    bodyFatMass: 0,
  };

  return (
    <BasicLayout>
      <div
        className="dashboard-container"
        data-theme={isDark ? "dark" : "light"}
      >
        {/* --- 헤더 --- */}
        <header className="dashboard-header">
          <Home className="icon-home" size={24} />

          <div className="header-right">
            <button className="btn-toggle-theme" onClick={toggleDarkMode}>
              {isDark ? <Sun size={20} /> : <Moon size={20} />}
            </button>
            <button className="btn-logout">로그아웃</button>
          </div>
        </header>

        {/* --- 메인 그리드 레이아웃 --- */}
        <div className="dashboard-main">
          {/* 로딩 및 에러 메시지 표시 */}
          {isLoading && (
            <div
              style={{
                padding: "20px",
                textAlign: "center",
                gridColumn: "1 / -1",
              }}
            >
              데이터를 불러오는 중...
            </div>
          )}
          {error && (
            <div
              style={{
                padding: "20px",
                color: "red",
                textAlign: "center",
                gridColumn: "1 / -1",
              }}
            >
              <strong>에러:</strong> {error}
              <br />
              <small>
                백엔드 서버가 실행 중인지 확인해주세요. (http://localhost:8080)
              </small>
            </div>
          )}

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
                {/* ✅ 날짜 표시 */}
                <div className="row date">
                  {info.measuredTime
                    ? new Date(info.measuredTime).toLocaleDateString("ko-KR")
                    : "측정 기록 없음"}
                </div>
                {/* ✅ 몸무게 표시 (height는 백엔드 DTO에 없음) */}
                <div className="row stats">
                  <span>{info.weight || 0} kg</span>
                </div>
              </div>
            </div>

            <div className="info-card">
              <h3 className="section-title">체성분 분석</h3>
              <div className="data-list">
                {/* ✅ 백엔드 DTO 필드값 바인딩 */}
                <DataRow
                  label="체수분(L)"
                  value={
                    info.bodyWater ? `${info.bodyWater.toFixed(2)} L` : "-"
                  }
                />
                <DataRow
                  label="단백질(kg)"
                  value={info.protein ? `${info.protein.toFixed(2)} kg` : "-"}
                />
                <DataRow
                  label="무기질(kg)"
                  value={info.minerals ? `${info.minerals.toFixed(2)} kg` : "-"}
                />
                <DataRow
                  label="체지방(kg)"
                  value={
                    info.bodyFatMass ? `${info.bodyFatMass.toFixed(2)} kg` : "-"
                  }
                />
              </div>
            </div>
          </aside>

          {/* ============ 오른쪽 차트 영역 ============ */}
          <main className="right-content">
            <div className="badge-row">
              <span className="lime-badge">인바디 자동분석</span>
            </div>

            <div className="charts-container">
              {/* ✅ historyData를 data prop으로 전달 */}
              <ChartRow
                title="체지방률"
                value={
                  info.bodyFatPercent
                    ? `${info.bodyFatPercent.toFixed(1)}%`
                    : "-"
                }
                chartTitle="체지방률 변화"
                dataKey="fatRate"
                strokeColor="#4A90E2"
                isDark={isDark}
                data={historyData}
              />
              <ChartRow
                title="골격근량"
                value={
                  info.skeletalMuscleMass
                    ? `${info.skeletalMuscleMass.toFixed(1)}kg`
                    : "-"
                }
                chartTitle="골격근량 변화"
                dataKey="muscle"
                strokeColor="#D0021B"
                isDark={isDark}
                data={historyData}
              />
              <ChartRow
                title="체중"
                value={info.weight ? `${info.weight.toFixed(1)}kg` : "-"}
                chartTitle="체중 변화"
                dataKey="weight"
                strokeColor="#7ED321"
                isDark={isDark}
                data={historyData}
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

// ✅ [수정] data prop을 받아서 SimpleLineChart로 넘김
function ChartRow({
  title,
  value,
  chartTitle,
  dataKey,
  strokeColor,
  isDark,
  data,
}) {
  return (
    <div className="chart-layout-row">
      <div className="grey-info-box">
        <div className="info-title">{title}</div>
        <div className="info-value">{value}</div>
      </div>

      <div className="chart-area">
        <div className="chart-main-title">{chartTitle}</div>
        <div style={{ width: "100%", height: "160px" }}>
          <SimpleLineChart
            dataKey={dataKey}
            stroke={strokeColor}
            isDark={isDark}
            data={data} // 차트 데이터 전달
          />
        </div>
      </div>
    </div>
  );
}

// ✅ [수정] data를 받아서 Recharts에 연결
function SimpleLineChart({ dataKey, stroke, isDark, data }) {
  const axisColor = isDark ? "#aaaaaa" : "#666";
  const gridColor = isDark ? "#444" : "#e0e0e0";

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart
        data={data} // 여기서 실제 데이터를 사용
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
          domain={["auto", "auto"]} // 데이터 범위에 따라 축 자동 조정
          tickCount={6}
        />
        <Tooltip
          contentStyle={{
            backgroundColor: isDark ? "#333" : "#fff",
            borderColor: isDark ? "#555" : "#ccc",
            color: isDark ? "#fff" : "#000",
          }}
          labelStyle={{ color: isDark ? "#fff" : "#000" }}
          formatter={(value, name) => {
            if (dataKey === "fatRate") return [`${value}%`, "체지방률"];
            if (dataKey === "muscle") return [`${value}kg`, "골격근량"];
            if (dataKey === "weight") return [`${value}kg`, "체중"];
            return [value, name];
          }}
        />
        <Line
          type="linear"
          dataKey={dataKey}
          stroke={stroke}
          strokeWidth={2}
          dot={false}
          name={
            dataKey === "fatRate"
              ? "체지방률"
              : dataKey === "muscle"
                ? "골격근량"
                : "체중"
          }
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

export default ProfileIndex;
