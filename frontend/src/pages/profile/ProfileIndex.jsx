import React, { useState, useEffect } from "react";
import "../../styles/Profile.css";
import BasicLayout from "../../components/layout/BasicLayout";
import { Home, User, Moon, Sun, X } from "lucide-react";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Tooltip,
} from "recharts";
import { getMyBodyInfoHistory, updateBodyInfo } from "../../services/bodyInfoApi";

const ProfileIndex = () => {
  const [isDark, setIsDark] = useState(false);
  const toggleDarkMode = () => setIsDark((prev) => !prev);

  const [historyData, setHistoryData] = useState([]);
  const [latestInfo, setLatestInfo] = useState(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editData, setEditData] = useState({});

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
    // 객체 깊은 복사 혹은 펼침 연산자로 새로운 객체 생성하여 전달
    setEditData({ ...latestInfo });
    setIsModalOpen(true);
  };

  // ✅ [수정 핵심] 안전한 숫자 변환 함수
  const safeParseFloat = (val) => {
    if (val === "" || val === null || val === undefined) return 0;
    const num = Number(val);
    return isNaN(num) ? 0 : num;
  };

 const handleSave = async (updatedData) => {
     try {
       // 1. 기존 데이터와 합치기
       const payload = {
         ...latestInfo,
         ...updatedData
       };

//      if (payload.measuredTime && typeof payload.measuredTime === 'string') {
//                payload.measuredTime = payload.measuredTime.replace('T', ' ').substring(0, 19);
//            }


       // 불필요한 BaseEntity 필드 제거 (안전책)
       delete payload.regDate;
       delete payload.modDate;

       // 2. 숫자로 변환 (문자열 전송 방지)
       payload.height = Number(payload.height);
       payload.weight = Number(payload.weight);
       payload.skeletalMuscleMass = Number(payload.skeletalMuscleMass);
       payload.bodyFatPercent = Number(payload.bodyFatPercent);
       payload.bodyWater = Number(payload.bodyWater);
       payload.protein = Number(payload.protein);
       payload.minerals = Number(payload.minerals);
       payload.bodyFatMass = Number(payload.bodyFatMass);

       payload.targetWeight = Number(payload.targetWeight);
       payload.weightControl = Number(payload.weightControl);
       payload.fatControl = Number(payload.fatControl);
       payload.muscleControl = Number(payload.muscleControl);

       console.log("🚀 최종 전송 Payload:", payload);

       // 3. 전송
       await updateBodyInfo(payload.id, payload);

       alert("성공적으로 수정되었습니다.");
       setIsModalOpen(false);
       fetchData();
     } catch (error) {
       // api.js가 출력한 상세 에러를 콘솔에서 확인 가능
       console.error("수정 실패:", error);
       alert(error.message || "수정 중 오류가 발생했습니다.");
     }
   };

  // 차트 데이터 가공
  const chartData = historyData.map((item) => ({
//     name: item.measuredTime ? item.measuredTime.substring(5, 10) : "",
    fatRate: item.bodyFatPercent,
    muscle: item.skeletalMuscleMass,
    weight: item.weight,
  }));

  const val = (v, unit = "") => (v !== null && v !== undefined ? `${v} ${unit}` : "-");

  const calculateAge = (birthDateString) => {
    if (!birthDateString) return "-";
    const birthYear = new Date(birthDateString).getFullYear();
    const currentYear = new Date().getFullYear();
    return currentYear - birthYear + 1;
  };

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
          {/* === 좌측 패널 === */}
          <aside className="left-sidebar">
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
                  <button
                    className="btn-edit"
                    onClick={handleEditClick}
                    style={{
                      fontSize: '12px', padding: '4px 8px', backgroundColor: '#e0e0e0',
                      border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px'
                    }}
                  >
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
                      <span style={{ fontWeight: '600', color: '#333' }}>{latestInfo.shipToName}</span> <br/>
                      [{latestInfo.shipZipcode}] {latestInfo.shipAddress1} {latestInfo.shipAddress2}
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="info-card">
              <h3 className="section-title">체성분 분석</h3>
              <div className="data-list">
                <DataRow label="체수분(L)" value={val(latestInfo?.bodyWater, "L")} />
                <DataRow label="단백질(kg)" value={val(latestInfo?.protein, "kg")} />
                <DataRow label="무기질(kg)" value={val(latestInfo?.minerals, "kg")} />
                <DataRow label="체지방(kg)" value={val(latestInfo?.bodyFatMass, "kg")} />
              </div>
            </div>

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

          {/* === 우측 패널 (차트) === */}
          <main className="right-content">
            <div className="badge-row">
              <span className="lime-badge">인바디 자동분석</span>
            </div>
            <div className="charts-container">
              <ChartRow title="체지방률" value={val(latestInfo?.bodyFatPercent, "%")}
                        chartTitle="체지방률 변화" data={chartData} dataKey="fatRate" strokeColor="#4A90E2" isDark={isDark} />
              <ChartRow title="골격근량" value={val(latestInfo?.skeletalMuscleMass, "kg")}
                        chartTitle="골격근량 변화" data={chartData} dataKey="muscle" strokeColor="#D0021B" isDark={isDark} />
              <ChartRow title="체중" value={val(latestInfo?.weight, "kg")}
                        chartTitle="체중 변화" data={chartData} dataKey="weight" strokeColor="#7ED321" isDark={isDark} />
            </div>
          </main>
        </div>

        {/* ✅ 모달 배치 */}
        {isModalOpen && (
          <BodyInfoModifyModal
            data={editData}
            onClose={() => setIsModalOpen(false)}
            onSave={handleSave}
          />
        )}

      </div>
    </BasicLayout>
  );
};

// --- Helper Components ---

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
        <XAxis dataKey="name" tickLine={false} axisLine={{ stroke: gridColor }} tick={{ fontSize: 12, fill: axisColor }} interval="preserveStartEnd" />
        <YAxis hide={false} tick={{ fontSize: 12, fill: axisColor }} axisLine={false} tickLine={false} domain={['auto', 'auto']} width={40} />
        <Tooltip
          contentStyle={{ backgroundColor: isDark ? "#333" : "#fff", borderColor: isDark ? "#555" : "#ccc", color: isDark ? "#fff" : "#000" }}
          formatter={(value) => [value, dataKey === "fatRate" ? "%" : "kg"]}
        />
        <Line type="monotone" dataKey={dataKey} stroke={stroke} strokeWidth={3} dot={{ r: 4, fill: stroke, strokeWidth: 0 }} activeDot={{ r: 6 }} isAnimationActive={true} />
      </LineChart>
    </ResponsiveContainer>
  );
}

// ✅ 모달 컴포넌트 수정
const BodyInfoModifyModal = ({ data, onClose, onSave }) => {
  // 초기값을 data로 설정하되, null 방지 처리
  const [formData, setFormData] = useState({
    height: '', weight: '', skeletalMuscleMass: '', bodyFatPercent: '',
    bodyWater: '', protein: '', minerals: '', bodyFatMass: '',
    shipToName: '', shipToPhone: '', shipZipcode: '', shipAddress1: '', shipAddress2: '',
    ...data // data가 있으면 덮어씌움
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value // 입력값을 그대로 저장 (문자열 상태 유지)
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSave(formData);
  };

  return (
    <div className="modal-overlay" style={{
      position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
      backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 9999
    }}>
      <div className="modal-content" style={{
        backgroundColor: 'white', padding: '30px', borderRadius: '10px', width: '500px',
        maxHeight: '90vh', overflowY: 'auto', position: 'relative', boxShadow: '0 4px 6px rgba(0,0,0,0.1)'
      }}>
        <button onClick={onClose} style={{ position: 'absolute', top: '15px', right: '15px', border: 'none', background: 'none', cursor: 'pointer' }}>
          <X size={24} />
        </button>

        <h2 style={{ marginBottom: '20px', textAlign: 'center', color: '#333' }}>신체 정보 수정</h2>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>

          <div className="form-section">
            <h4 style={{borderBottom:'1px solid #ddd', paddingBottom:'5px', marginBottom:'10px', color: '#666'}}>기본 정보</h4>
            <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:'10px'}}>
                <InputGroup label="키 (cm)" name="height" value={formData.height} onChange={handleChange} />
                <InputGroup label="몸무게 (kg)" name="weight" value={formData.weight} onChange={handleChange} />
                <InputGroup label="골격근량 (kg)" name="skeletalMuscleMass" value={formData.skeletalMuscleMass} onChange={handleChange} />
                <InputGroup label="체지방률 (%)" name="bodyFatPercent" value={formData.bodyFatPercent} onChange={handleChange} />
            </div>
          </div>

          <div className="form-section">
            <h4 style={{borderBottom:'1px solid #ddd', paddingBottom:'5px', marginBottom:'10px', color: '#666'}}>상세 정보</h4>
            <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:'10px'}}>
                <InputGroup label="체수분 (L)" name="bodyWater" value={formData.bodyWater} onChange={handleChange} />
                <InputGroup label="단백질 (kg)" name="protein" value={formData.protein} onChange={handleChange} />
                <InputGroup label="무기질 (kg)" name="minerals" value={formData.minerals} onChange={handleChange} />
                <InputGroup label="체지방량 (kg)" name="bodyFatMass" value={formData.bodyFatMass} onChange={handleChange} />
            </div>
          </div>

          <div className="form-section">
            <h4 style={{borderBottom:'1px solid #ddd', paddingBottom:'5px', marginBottom:'10px', color: '#666'}}>배송지 정보</h4>
            <InputGroup label="받는 분" name="shipToName" value={formData.shipToName} onChange={handleChange} />
            <InputGroup label="연락처" name="shipToPhone" value={formData.shipToPhone} onChange={handleChange} />
            <InputGroup label="우편번호" name="shipZipcode" value={formData.shipZipcode} onChange={handleChange} />
            <InputGroup label="주소" name="shipAddress1" value={formData.shipAddress1} onChange={handleChange} />
            <InputGroup label="상세주소" name="shipAddress2" value={formData.shipAddress2} onChange={handleChange} />
          </div>

          <button type="submit" style={{
            marginTop: '10px', padding: '12px', backgroundColor: '#ccff00',
            border: 'none', borderRadius: '5px', fontWeight: 'bold', cursor: 'pointer', fontSize: '16px', color:'#000'
          }}>
            저장하기
          </button>
        </form>
      </div>
    </div>
  );
};

const InputGroup = ({ label, name, value, onChange }) => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
    <label style={{ fontSize: '12px', color: '#666', fontWeight:'bold' }}>{label}</label>
    <input
      // 텍스트 필드인지 숫자 필드인지 판별
      type={name.includes('Name') || name.includes('Address') || name.includes('Phone') || name.includes('Zipcode') ? "text" : "number"}
      step="0.1"
      name={name}
      // 값이 null/undefined일 때 빈 문자열로 처리 (수정 불가 버그 방지)
      value={value !== null && value !== undefined ? value : ''}
      onChange={onChange}
      style={{ padding: '8px', border: '1px solid #ddd', borderRadius: '4px', fontSize:'14px' }}
    />
  </div>
);

export default ProfileIndex;