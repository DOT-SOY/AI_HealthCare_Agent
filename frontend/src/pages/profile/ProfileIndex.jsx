import React, { useState, useEffect } from "react";
import "../../styles/Profile.css";
import BasicLayout from "../../components/layout/BasicLayout";
import { Home, User, Moon, Sun, X } from "lucide-react";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Tooltip,
} from "recharts";
import { getMyBodyInfoHistory, updateBodyInfo } from "../../services/bodyInfoApi";
import {
  createMemberInfoAddr,
  updateMemberInfoAddr,
  getMemberInfoAddrList,
  setDefaultMemberInfoAddr,
  deleteMemberInfoAddr,
} from "../../services/memberInfoAddrApi";

const ProfileIndex = () => {
  const [isDark, setIsDark] = useState(false);
  const toggleDarkMode = () => setIsDark((prev) => !prev);

  const [historyData, setHistoryData] = useState([]);
  const [latestInfo, setLatestInfo] = useState(null);
  const [addrList, setAddrList] = useState([]);

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

  const fetchAddrList = async (memberId) => {
    try {
      if (!memberId) {
        setAddrList([]);
        return;
      }
      const list = await getMemberInfoAddrList(memberId);
      setAddrList(Array.isArray(list) ? list : []);
    } catch (error) {
      console.error("배송지 목록 로딩 실패:", error);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  useEffect(() => {
    fetchAddrList(latestInfo?.memberId);
  }, [latestInfo?.memberId]);

  const handleEditClick = () => {
    if (!latestInfo) {
      alert("수정할 데이터가 없습니다.");
      return;
    }
    // 객체 깊은 복사 혹은 펼침 연산자로 새로운 객체 생성하여 전달
    setEditData({ ...latestInfo });
    setIsModalOpen(true);
    fetchAddrList(latestInfo?.memberId);
  };

  const handleSaveBodyInfo = async (updatedData, targetId) => {
    try {
      const bodyId = targetId ?? latestInfo?.id;
      if (!bodyId) {
        alert("수정할 데이터가 없습니다.");
        return;
      }

      const toNumberOrNull = (val) => {
        if (val === "" || val === null || val === undefined) return null;
    const num = Number(val);
        return Number.isNaN(num) ? null : num;
  };

      const bodyPayload = {
        height: toNumberOrNull(updatedData.height),
        weight: toNumberOrNull(updatedData.weight),
        purpose: updatedData.purpose || null,
      };

      await updateBodyInfo(bodyId, bodyPayload);
      alert("신체 정보가 수정되었습니다.");
      await fetchData();
    } catch (error) {
      console.error("신체 정보 수정 실패:", error);
      alert(error.message || "신체 정보 수정 중 오류가 발생했습니다.");
    }
  };

  const handleSaveAddress = async (addrData, editingAddrId) => {
    try {
      const memberId = latestInfo?.memberId;
      if (!memberId) {
        throw new Error("memberId가 없어 배송지 정보를 저장할 수 없습니다.");
      }

      const addrPayload = {
        memberId,
        recipientName: addrData.recipientName?.trim() || "",
        recipientPhone: addrData.recipientPhone?.trim() || "",
        zipcode: addrData.zipcode?.trim() || "",
        address1: addrData.address1?.trim() || "",
        address2: addrData.address2?.trim() || "",
      };

      if (!addrPayload.recipientName || !addrPayload.recipientPhone || !addrPayload.zipcode || !addrPayload.address1) {
        alert("배송지 필수 정보를 모두 입력해주세요.");
        return false;
      }

      if (editingAddrId) {
        await updateMemberInfoAddr(editingAddrId, addrPayload);
      } else {
        await createMemberInfoAddr(addrPayload);
      }

      await fetchAddrList(memberId);
      alert(editingAddrId ? "배송지가 수정되었습니다." : "배송지가 추가되었습니다.");
      return true;
    } catch (error) {
      console.error("배송지 저장 실패:", error);
      alert(error.message || "배송지 저장 중 오류가 발생했습니다.");
      return false;
    }
  };

  const handleSetDefaultAddress = async (addrId) => {
    try {
      await setDefaultMemberInfoAddr(addrId);
      await fetchAddrList(latestInfo?.memberId);
      alert("기본 배송지가 변경되었습니다.");
    } catch (error) {
      console.error("기본 배송지 설정 실패:", error);
      alert(error.message || "기본 배송지 설정 중 오류가 발생했습니다.");
    }
  };

  const handleDeleteAddress = async (addrId) => {
    if (!window.confirm("정말로 이 배송지를 삭제하시겠습니까?")) {
      return;
    }
    try {
      await deleteMemberInfoAddr(addrId);
      await fetchAddrList(latestInfo?.memberId);
      alert("배송지가 삭제되었습니다.");
     } catch (error) {
      console.error("배송지 삭제 실패:", error);
      alert(error.message || "배송지 삭제 중 오류가 발생했습니다.");
     }
  };

  const formatMeasuredDate = (value) => {
    if (!value) return "";
    const text = String(value);
    return text.length >= 10 ? text.substring(5, 10) : text;
   };

  // 차트 데이터 가공
  const chartData = historyData.map((item) => ({
    name: formatMeasuredDate(item.measuredTime),
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
                <div className="row stats" style={{ marginTop: '4px', color: '#666' }}>
                  운동목적: {latestInfo?.purpose === "DIET" ? "다이어트" : latestInfo?.purpose === "MAINTENANCE" ? "유지" : latestInfo?.purpose === "BULK_UP" ? "벌크업" : "-"}
                    </div>

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
            addrList={addrList}
            onClose={() => setIsModalOpen(false)}
            onSaveBody={handleSaveBodyInfo}
            onSaveAddr={handleSaveAddress}
            onSetDefaultAddr={handleSetDefaultAddress}
            onDeleteAddr={handleDeleteAddress}
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
const BodyInfoModifyModal = ({ data, addrList, onClose, onSaveBody, onSaveAddr, onSetDefaultAddr, onDeleteAddr }) => {
  const [bodyForm, setBodyForm] = useState({
    height: '',
    weight: '',
    purpose: '',
  });
  const emptyAddrForm = {
    recipientName: '',
    recipientPhone: '',
    zipcode: '',
    address1: '',
    address2: '',
  };
  const [addrForm, setAddrForm] = useState({ ...emptyAddrForm });
  const [editingAddrId, setEditingAddrId] = useState(null);
  const [isAddrModalOpen, setIsAddrModalOpen] = useState(false);

  useEffect(() => {
    setBodyForm({
      height: data?.height ?? '',
      weight: data?.weight ?? '',
      purpose: data?.purpose ?? '',
    });
  }, [data?.height, data?.weight, data?.purpose]);

  const openAddrModalForCreate = () => {
    setEditingAddrId(null);
    setAddrForm({ ...emptyAddrForm });
    setIsAddrModalOpen(true);
  };

  const openAddrModalForEdit = (addr) => {
    setEditingAddrId(addr.id);
    setAddrForm({
      recipientName: addr.recipientName ?? '',
      recipientPhone: addr.recipientPhone ?? '',
      zipcode: addr.zipcode ?? '',
      address1: addr.address1 ?? '',
      address2: addr.address2 ?? '',
    });
    setIsAddrModalOpen(true);
  };

  const closeAddrModal = () => {
    setIsAddrModalOpen(false);
    setEditingAddrId(null);
    setAddrForm({ ...emptyAddrForm });
  };

  const handleBodyChange = (e) => {
    const { name, value } = e.target;
    setBodyForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleAddrChange = (e) => {
    const { name, value } = e.target;
    setAddrForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleBodySubmit = async (e) => {
    e.preventDefault();
    await onSaveBody(bodyForm, data?.id);
  };

  const handleAddrSubmit = async (e) => {
    e.preventDefault();
    const saved = await onSaveAddr(addrForm, editingAddrId);
    if (saved) {
      closeAddrModal();
    }
  };

  const handleEditAddr = (addr) => {
    openAddrModalForEdit(addr);
  };

  const safeAddrList = Array.isArray(addrList) ? addrList : [];
  const sortedAddrList = [...safeAddrList].sort((a, b) => {
    const defaultDiff = Number(b.isDefault) - Number(a.isDefault);
    if (defaultDiff !== 0) return defaultDiff;
    return (b.id || 0) - (a.id || 0);
  });

  return (
    <div className="modal-overlay" style={{
      position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
      backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 9999
    }}>
      <div className="modal-content" style={{
        backgroundColor: 'white', padding: '30px', borderRadius: '10px', width: '560px',
        maxHeight: '90vh', overflowY: 'auto', position: 'relative', boxShadow: '0 4px 6px rgba(0,0,0,0.1)'
      }}>
        <button onClick={onClose} style={{ position: 'absolute', top: '15px', right: '15px', border: 'none', background: 'none', cursor: 'pointer' }}>
          <X size={24} />
        </button>

        <h2 style={{ marginBottom: '20px', textAlign: 'center', color: '#333' }}>회원정보 수정</h2>

        <form onSubmit={handleBodySubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
          <div className="form-section">
            <h4 style={{ borderBottom: '1px solid #ddd', paddingBottom: '5px', marginBottom: '10px', color: '#666' }}>기본 정보</h4>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <InputGroup label="키 (cm)" name="height" value={bodyForm.height} onChange={handleBodyChange} />
              <InputGroup label="몸무게 (kg)" name="weight" value={bodyForm.weight} onChange={handleBodyChange} />
              <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
                <label style={{ fontSize: '12px', color: '#666', fontWeight: 'bold' }}>운동 목적</label>
                <select
                  name="purpose"
                  value={bodyForm.purpose || ""}
                  onChange={handleBodyChange}
                  style={{ padding: '8px', border: '1px solid #ddd', borderRadius: '4px', fontSize: '14px' }}
                >
                  <option value="">선택 안 함</option>
                  <option value="DIET">다이어트</option>
                  <option value="MAINTENANCE">유지</option>
                  <option value="BULK_UP">벌크업</option>
                </select>
              </div>
            </div>
          </div>

          <button type="submit" style={{
            marginTop: '4px', padding: '10px', backgroundColor: '#ccff00',
            border: 'none', borderRadius: '5px', fontWeight: 'bold', cursor: 'pointer', fontSize: '15px', color: '#000'
          }}>
            신체 정보 저장
          </button>
        </form>

        <div className="form-section" style={{ marginTop: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
            <h4 style={{ margin: 0, color: '#666' }}>배송지 목록</h4>
            <button
              type="button"
              onClick={openAddrModalForCreate}
              style={{ fontSize: '12px', padding: '4px 8px', backgroundColor: '#e0e0e0', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer' }}
            >
              + 배송지 추가
            </button>
          </div>

          {sortedAddrList.length === 0 ? (
            <div style={{ fontSize: '13px', color: '#888' }}>등록된 배송지가 없습니다.</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {sortedAddrList.map((addr) => (
                <div key={addr.id} style={{ padding: '8px 10px', border: '1px solid #eee', borderRadius: '6px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontWeight: 600 }}>
                      {addr.recipientName}
                      {addr.isDefault && (
                        <span style={{ marginLeft: '6px', fontSize: '11px', color: '#2E7D32', background: '#E8F5E9', padding: '2px 6px', borderRadius: '4px' }}>
                          기본배송지
                        </span>
                      )}
                    </span>
                    <div style={{ display: 'flex', gap: '6px' }}>
                      {!addr.isDefault && (
                        <button
                          type="button"
                          onClick={() => onSetDefaultAddr(addr.id)}
                          style={{ fontSize: '11px', padding: '3px 6px', backgroundColor: '#f0f0f0', border: '1px solid #ccc', borderRadius: '3px', cursor: 'pointer' }}
                        >
                          기본 설정
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => handleEditAddr(addr)}
                        style={{ fontSize: '11px', padding: '3px 6px', backgroundColor: '#f0f0f0', border: '1px solid #ccc', borderRadius: '3px', cursor: 'pointer' }}
                      >
                        수정
                      </button>
                      <button
                        type="button"
                        onClick={() => onDeleteAddr(addr.id)}
                        style={{ fontSize: '11px', padding: '3px 6px', backgroundColor: '#f0f0f0', border: '1px solid #ccc', borderRadius: '3px', cursor: 'pointer', color: 'red' }}
                      >
                        삭제
                      </button>
                    </div>
                  </div>
                  <div style={{ fontSize: '12px', color: '#666', marginTop: '4px' }}>
                    [{addr.zipcode}] {addr.address1} {addr.address2}
                  </div>
                  <div style={{ fontSize: '12px', color: '#666', marginTop: '2px' }}>
                    {addr.recipientPhone}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
        <AddressEditModal
          isOpen={isAddrModalOpen}
          isEditing={Boolean(editingAddrId)}
          formData={addrForm}
          onChange={handleAddrChange}
          onClose={closeAddrModal}
          onSubmit={handleAddrSubmit}
        />
            </div>
          </div>
  );
};

const AddressEditModal = ({ isOpen, isEditing, formData, onChange, onClose, onSubmit }) => {
  if (!isOpen) return null;

  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
      backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 10001
    }}>
      <div style={{
        backgroundColor: '#fff', padding: '20px', borderRadius: '8px', width: '420px',
        boxShadow: '0 4px 10px rgba(0,0,0,0.15)', position: 'relative'
      }}>
        <button onClick={onClose} style={{ position: 'absolute', top: '10px', right: '10px', border: 'none', background: 'none', cursor: 'pointer' }}>
          <X size={20} />
        </button>
        <h3 style={{ marginTop: 0, marginBottom: '15px', textAlign: 'center', color: '#333' }}>
          {isEditing ? "배송지 수정" : "배송지 추가"}
        </h3>
        <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <InputGroup label="받는 분" name="recipientName" value={formData.recipientName} onChange={onChange} />
          <InputGroup label="연락처" name="recipientPhone" value={formData.recipientPhone} onChange={onChange} />
          <InputGroup label="우편번호" name="zipcode" value={formData.zipcode} onChange={onChange} />
          <InputGroup label="주소" name="address1" value={formData.address1} onChange={onChange} />
          <InputGroup label="상세주소" name="address2" value={formData.address2} onChange={onChange} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '8px' }}>
          <button type="submit" style={{
              padding: '8px 12px', backgroundColor: '#e0e0e0',
              border: '1px solid #ccc', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer', fontSize: '13px', color: '#000'
            }}>
              {isEditing ? "수정" : "추가"}
            </button>
            <button type="button" onClick={onClose} style={{
              padding: '8px 12px', backgroundColor: '#fafafa',
              border: '1px solid #ccc', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer', fontSize: '13px', color: '#333'
          }}>
              취소
          </button>
          </div>
        </form>
      </div>
    </div>
  );
};

const InputGroup = ({ label, name, value, onChange }) => {
  const isTextField = /name|address|phone|zipcode/i.test(name);
  const inputType = isTextField ? "text" : "number";

  return (
  <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
    <label style={{ fontSize: '12px', color: '#666', fontWeight:'bold' }}>{label}</label>
    <input
        type={inputType}
        step={inputType === "number" ? "0.1" : undefined}
      name={name}
      // 값이 null/undefined일 때 빈 문자열로 처리 (수정 불가 버그 방지)
      value={value !== null && value !== undefined ? value : ''}
      onChange={onChange}
      style={{ padding: '8px', border: '1px solid #ddd', borderRadius: '4px', fontSize:'14px' }}
    />
  </div>
);
};

export default ProfileIndex;