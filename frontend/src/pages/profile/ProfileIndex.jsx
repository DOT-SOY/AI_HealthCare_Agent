import React, { useState, useEffect, useRef } from "react";
import "../../styles/Profile.css";
import BasicLayout from "../../components/layout/BasicLayout";
import { Home, User, Moon, Sun, X, Camera } from "lucide-react";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Tooltip,
} from "recharts";
import { getMyBodyInfoHistory, updateBodyInfo } from "../../services/bodyInfoApi";
// ✅ axios 직접 사용 (파일 업로드는 별도 설정이 편함) 또는 기존 api.js 활용 가능
import axios from "axios";

const ProfileIndex = () => {
  const [isDark, setIsDark] = useState(false);
  const toggleDarkMode = () => setIsDark((prev) => !prev);

  const [historyData, setHistoryData] = useState([]);
  const [latestInfo, setLatestInfo] = useState(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editData, setEditData] = useState({});

  // ✅ OCR 로딩 상태 및 파일 input ref
  const [isOcrLoading, setIsOcrLoading] = useState(false);
  const fileInputRef = useRef(null);

  const fetchData = async () => {
    try {
      const data = await getMyBodyInfoHistory();
      if (data && data.length > 0) {
        setHistoryData(data);
        setLatestInfo(data[data.length - 1]);
      }
    } catch (error) {
      console.error("데이터 로딩 실패:", error);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleEditClick = () => {
    if (!latestInfo) {
      alert("수정할 데이터가 없습니다.");
      return;
    }
    setEditData({ ...latestInfo });
    setIsModalOpen(true);
  };

  const handleSave = async (updatedData) => {
    try {
      const payload = {
        ...updatedData,
        height: Number(updatedData.height),
        weight: Number(updatedData.weight),
        skeletalMuscleMass: Number(updatedData.skeletalMuscleMass),
        bodyFatPercent: Number(updatedData.bodyFatPercent),
        bodyWater: Number(updatedData.bodyWater),
        protein: Number(updatedData.protein),
        minerals: Number(updatedData.minerals),
        bodyFatMass: Number(updatedData.bodyFatMass),
        targetWeight: Number(updatedData.targetWeight),
        weightControl: Number(updatedData.weightControl),
        fatControl: Number(updatedData.fatControl),
        muscleControl: Number(updatedData.muscleControl),
      };

      await updateBodyInfo(updatedData.id, payload);
      alert("수정되었습니다.");
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error("수정 실패:", error);
      alert("수정 중 오류가 발생했습니다.");
    }
  };

  // ✅ [OCR] 버튼 클릭 (숨겨진 input 실행)
  const handleOcrClick = () => {
    fileInputRef.current.click();
  };

  // ✅ [OCR] 파일 선택 및 백엔드 전송
  const handleFileChange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    setIsOcrLoading(true);
    const formData = new FormData();
    formData.append("file", file);

    try {
      // 1. 백엔드로 이미지 전송 (GPT 분석 요청)
      // (경로 주의: /api/ocr/analyze)
      const res = await axios.post("http://localhost:8080/api/ocr/analyze", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
          // 토큰이 필요하다면 여기에 Authorization 헤더 추가
          // Authorization: `Bearer ${localStorage.getItem('accessToken')}`
        },
      });

      const extractedData = res.data;
      console.log("GPT 분석 결과:", extractedData);

      // 2. 결과 데이터로 모달 데이터 세팅
      setEditData((prev) => ({
        ...latestInfo, // 기존 정보(배송지 등) 유지
        ...extractedData, // 분석된 숫자로 덮어쓰기
      }));

      alert("분석 완료! 내용을 확인하고 저장해주세요.");
      setIsModalOpen(true); // 수정 모달 띄우기

    } catch (error) {
      console.error("OCR 분석 실패:", error);
      alert("이미지 분석에 실패했습니다. (API 키 혹은 파일 크기를 확인하세요)");
    } finally {
      setIsOcrLoading(false);
      e.target.value = ""; // 초기화 (같은 파일 다시 선택 가능하게)
    }
  };



 // OCR 요청 함수 (type: gpt, google, paddle, easy)
  const handleOcrRequest = async (e, type) => {
    const file = e.target.files[0];
    if (!file) return;

    setIsOcrLoading(true);
    const formData = new FormData();
    formData.append("file", file);

    try {
      // 주소를 타입에 따라 다르게 호출 (/api/ocr/gpt, /api/ocr/paddle 등)
      const res = await axios.post(`http://localhost:8080/api/ocr/${type}`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });

      console.log(`${type} 분석 결과:`, res.data);
      alert("콘솔창(F12)에서 결과를 확인해보세요!");

      // 결과 파싱 로직은 여기에 추가 (res.data에서 텍스트 추출 후 setEditData)

    } catch (error) {
      console.error("OCR 에러:", error);
      alert("분석 실패");
    } finally {
      setIsOcrLoading(false);
      e.target.value = "";
    }
  };



  // ... 차트 데이터 등 ...
  const calculateAge = (birthDateString) => {
    if (!birthDateString) return "-";
    const birthYear = new Date(birthDateString).getFullYear();
    const currentYear = new Date().getFullYear();
    return currentYear - birthYear + 1;
  };
  const chartData = historyData.map((item) => ({
    name: item.measuredTime ? item.measuredTime.substring(5, 10) : "",
    fatRate: item.bodyFatPercent,
    muscle: item.skeletalMuscleMass,
    weight: item.weight,
  }));
  const val = (v, unit = "") => (v !== null && v !== undefined ? `${v} ${unit}` : "-");

  return (
    <BasicLayout>
      <div className="dashboard-container" data-theme={isDark ? "dark" : "light"}>
        <header className="dashboard-header">
          <Home className="icon-home" size={24} />
          <div className="header-right">
            <button className="btn-toggle-theme" onClick={toggleDarkMode}>
              {isDark ? <Sun size={20} /> : <Moon size={20} />}
            </button>
            <button className="btn-logout">로그아웃</button>
          </div>
        </header>

        <div className="dashboard-main">
          <aside className="left-sidebar">
            {/* 회원정보 */}
            <div className="info-card">
              <div className="card-header">
                <h2>회원정보</h2>
              </div>
              <div className="card-content profile-details">
                <div className="row name-row" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span className="name" style={{ fontSize: '20px', fontWeight: 'bold' }}>
                      {latestInfo?.memberName || "사용자"}
                    </span>
                    <span className="gender-icon" style={{ fontSize: '14px', color: '#666' }}>
                      {latestInfo?.gender === "MALE" ? "♂ 남성" : latestInfo?.gender === "FEMALE" ? "♀ 여성" : "-"}
                    </span>
                  </div>
                  <button className="btn-edit" onClick={handleEditClick} style={{fontSize: '12px', padding: '4px 8px', backgroundColor: '#e0e0e0', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px'}}>
                    <User size={12} /> 수정
                  </button>
                </div>
                <div className="row date" style={{ marginTop: '5px', color: '#888' }}>
                  {latestInfo?.birthDate} ({calculateAge(latestInfo?.birthDate)}세)
                </div>
                <div className="row stats">
                  <span>{val(latestInfo?.height, "cm")}</span> &nbsp;/&nbsp; <span>{val(latestInfo?.weight, "kg")}</span>
                </div>
                {latestInfo?.shipAddress1 && (
                  <div style={{ marginTop: '15px', paddingTop: '12px', borderTop: '1px solid rgba(0,0,0,0.1)' }}>
                    <div style={{ fontSize: '13px', lineHeight: '1.5', color: '#666' }}>
                      <span style={{ fontWeight: '600' }}>{latestInfo.shipToName}</span> <br/>
                      {latestInfo.shipAddress1} {latestInfo.shipAddress2}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* 체성분 */}
            <div className="info-card">
              <h3 className="section-title">체성분 분석</h3>
              <div className="data-list">
                <DataRow label="체수분(L)" value={val(latestInfo?.bodyWater, "L")} />
                <DataRow label="단백질(kg)" value={val(latestInfo?.protein, "kg")} />
                <DataRow label="무기질(kg)" value={val(latestInfo?.minerals, "kg")} />
                <DataRow label="체지방(kg)" value={val(latestInfo?.bodyFatMass, "kg")} />
              </div>
            </div>

            {/* 체중조절 */}
            <div className="info-card">
              <h3 className="section-title">체중조절</h3>
              <div className="data-list">
                <DataRow label="적정체중" value={val(latestInfo?.targetWeight, "kg")} />
                <DataRow label="체중조절" value={val(latestInfo?.weightControl, "kg")} />
                <DataRow label="지방조절" value={val(latestInfo?.fatControl, "kg")} />
                <DataRow label="근육조절" value={val(latestInfo?.muscleControl, "kg")} />
              </div>
            </div>
          </aside>

          <main className="right-content">
            {/* ✅ 인바디 자동분석 버튼 (GPT OCR) */}
            <div className="badge-row">
              <input
                type="file"
                ref={fileInputRef}
                style={{ display: 'none' }}
                accept="image/*"
                onChange={handleFileChange}
              />
              <button
                className="lime-badge"
                onClick={handleOcrClick}
                disabled={isOcrLoading}
                style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px' }}
              >
                {isOcrLoading ? <span>분석 중... ⏳</span> : <><Camera size={20} /> 인바디 자동분석</>}
              </button>

               {/* 숨겨진 Input들을 각각 만들거나, 하나를 공유해서 state로 분기 처리해도 됨 */}
                            {/* 테스트 편의상 버튼 4개 예시 */}

                            <OCRButton label="GPT-4o" type="gpt" onChange={handleOcrRequest} />
                            <OCRButton label="Google" type="google" onChange={handleOcrRequest} />
                            <OCRButton label="Paddle" type="paddle" onChange={handleOcrRequest} />
                            <OCRButton label="Easy" type="easy" onChange={handleOcrRequest} />

            </div>

            <div className="charts-container">
              <ChartRow title="체지방률" value={val(latestInfo?.bodyFatPercent, "%")} chartTitle="체지방률 변화" data={chartData} dataKey="fatRate" strokeColor="#4A90E2" isDark={isDark} />
              <ChartRow title="골격근량" value={val(latestInfo?.skeletalMuscleMass, "kg")} chartTitle="골격근량 변화" data={chartData} dataKey="muscle" strokeColor="#D0021B" isDark={isDark} />
              <ChartRow title="체중" value={val(latestInfo?.weight, "kg")} chartTitle="체중 변화" data={chartData} dataKey="weight" strokeColor="#7ED321" isDark={isDark} />
            </div>
          </main>
        </div>

        {/* 수정 모달 */}
        {isModalOpen && (
          <BodyInfoModifyModal data={editData} onClose={() => setIsModalOpen(false)} onSave={handleSave} />
        )}
      </div>
    </BasicLayout>
  );
};

// ... Helper Components (DataRow, ChartRow, SimpleLineChart, BodyInfoModifyModal) ...
// (기존 코드와 동일하므로 생략, 파일에는 포함되어 있다고 가정)
// 만약 모달 컴포넌트 코드가 필요하면 이전 답변 참고하여 그대로 두시면 됩니다.

const BodyInfoModifyModal = ({ data, onClose, onSave }) => {
    const [formData, setFormData] = useState({ ...data });
    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    };
    const handleSubmit = (e) => {
        e.preventDefault();
        onSave(formData);
    };
    return (
        <div className="modal-overlay" style={{position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 9999}}>
            <div className="modal-content" style={{backgroundColor: 'white', padding: '30px', borderRadius: '10px', width: '500px', maxHeight: '90vh', overflowY: 'auto', position: 'relative'}}>
                <button onClick={onClose} style={{ position: 'absolute', top: '15px', right: '15px', border: 'none', background: 'none', cursor: 'pointer' }}><X size={24} /></button>
                <h2 style={{ marginBottom: '20px', textAlign: 'center', color:'#333' }}>신체 정보 수정</h2>
                <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                    <div className="form-section">
                        <h4 style={{borderBottom:'1px solid #ddd', paddingBottom:'5px', marginBottom:'10px', color:'#666'}}>기본 정보</h4>
                        <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:'10px'}}>
                            <InputGroup label="키 (cm)" name="height" value={formData.height} onChange={handleChange} />
                            <InputGroup label="몸무게 (kg)" name="weight" value={formData.weight} onChange={handleChange} />
                            <InputGroup label="골격근량 (kg)" name="skeletalMuscleMass" value={formData.skeletalMuscleMass} onChange={handleChange} />
                            <InputGroup label="체지방률 (%)" name="bodyFatPercent" value={formData.bodyFatPercent} onChange={handleChange} />
                        </div>
                    </div>
                    <div className="form-section">
                        <h4 style={{borderBottom:'1px solid #ddd', paddingBottom:'5px', marginBottom:'10px', color:'#666'}}>상세 정보</h4>
                        <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:'10px'}}>
                            <InputGroup label="체수분 (L)" name="bodyWater" value={formData.bodyWater} onChange={handleChange} />
                            <InputGroup label="단백질 (kg)" name="protein" value={formData.protein} onChange={handleChange} />
                            <InputGroup label="무기질 (kg)" name="minerals" value={formData.minerals} onChange={handleChange} />
                            <InputGroup label="체지방량 (kg)" name="bodyFatMass" value={formData.bodyFatMass} onChange={handleChange} />
                        </div>
                    </div>
                    <button type="submit" style={{marginTop: '10px', padding: '12px', backgroundColor: '#ccff00', border: 'none', borderRadius: '5px', fontWeight: 'bold', cursor: 'pointer', fontSize: '16px', color:'#000'}}>저장하기</button>
                </form>
            </div>
        </div>
    );
};
const InputGroup = ({ label, name, value, onChange }) => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
        <label style={{ fontSize: '12px', color: '#666', fontWeight:'bold' }}>{label}</label>
        <input type="number" step="0.1" name={name} value={value || ''} onChange={onChange} style={{ padding: '8px', border: '1px solid #ddd', borderRadius: '4px', fontSize:'14px' }} />
    </div>
);
// ChartRow, DataRow, SimpleLineChart 등은 기존 파일에 있는 것을 유지하세요.
// --- [누락된 Helper 컴포넌트들] ---

function DataRow({ label, value }) {
  return (
    <div className="data-row">
      <span className="label">{label}</span>
      <span className="value">{value}</span>
    </div>
  );
}

function ChartRow({ title, value, chartTitle, data, dataKey, strokeColor, isDark }) {
  return (
    <div className="chart-layout-row">
      <div className="grey-info-box">
        <div className="info-title">{title}</div>
        <div className="info-value">{value}</div>
      </div>
      <div className="chart-area">
        <div className="chart-main-title">{chartTitle}</div>
        <div style={{ width: "100%", height: "160px" }}>
          <SimpleLineChart data={data} dataKey={dataKey} stroke={strokeColor} isDark={isDark} />
        </div>
      </div>
    </div>
  );
}

function SimpleLineChart({ data, dataKey, stroke, isDark }) {
  const axisColor = isDark ? "#aaaaaa" : "#666";
  const gridColor = isDark ? "#444" : "#e0e0e0";

  if (!data || data.length === 0) {
    return (
      <div style={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center", color: axisColor, fontSize: "14px" }}>
        데이터가 없습니다.
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke={gridColor} strokeDasharray="3 3" />
        <XAxis
          dataKey="name"
          tickLine={false}
          axisLine={{ stroke: gridColor }}
          tick={{ fontSize: 12, fill: axisColor }}
          interval="preserveStartEnd"
        />
        <YAxis
          hide={false}
          tick={{ fontSize: 12, fill: axisColor }}
          axisLine={false}
          tickLine={false}
          domain={['auto', 'auto']}
          width={40}
        />
        <Tooltip
          contentStyle={{
            backgroundColor: isDark ? "#333" : "#fff",
            borderColor: isDark ? "#555" : "#ccc",
            color: isDark ? "#fff" : "#000",
          }}
          formatter={(value) => [value, dataKey === "fatRate" ? "%" : "kg"]}
        />
        <Line
          type="monotone"
          dataKey={dataKey}
          stroke={stroke}
          strokeWidth={3}
          dot={{ r: 4, fill: stroke, strokeWidth: 0 }}
          activeDot={{ r: 6 }}
          isAnimationActive={true}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
const OCRButton = ({ label, type, onChange }) => {
  const inputRef = React.useRef(null);
  return (
    <>
      <input
        type="file"
        ref={inputRef}
        style={{ display: 'none' }}
        onChange={(e) => onChange(e, type)}
      />
      <button
        className="lime-badge"
        style={{ fontSize: '14px', padding: '5px 10px', cursor: 'pointer' }}
        onClick={() => inputRef.current.click()}
      >
        {label}
      </button>
    </>
  )
}
export default ProfileIndex;