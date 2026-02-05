import React, { useState, useEffect } from "react";
import { useDispatch } from "react-redux";
import BasicLayout from "../../components/layout/BasicLayout";
import { Home, User, Moon, Sun, X, Plus, Edit, Trash2 } from "lucide-react";
import { logout } from "../../slices/loginSlice";
import { User, Moon, Sun, X, Plus, Edit, Trash2 } from "lucide-react";
import AddressSearchModal from "../../components/common/AddressSearchModal";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Tooltip,
} from "recharts";
import { getMyBodyInfoHistory, updateBodyInfo } from "../../services/bodyInfoApi";
import {
  getMemberInfoAddrList,
  createMemberInfoAddr,
  updateMemberInfoAddr,
  deleteMemberInfoAddr,
  setDefaultMemberInfoAddr
} from "../../services/memberInfoAddrApi";

const ProfileIndex = () => {
  const dispatch = useDispatch();
  const [isDark, setIsDark] = useState(false);
  const toggleDarkMode = () => setIsDark((prev) => !prev);

  const handleLogout = async () => {
    if (!window.confirm("로그아웃 하시겠습니까?")) return;
    await dispatch(logout());
  };

  const [historyData, setHistoryData] = useState([]);
  const [latestInfo, setLatestInfo] = useState(null);

  // 대시보드 차트/레이아웃에서 사용할 다크 모드 플래그
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editData, setEditData] = useState({});

  // 배송지 관련 상태
  const [addressList, setAddressList] = useState([]);
  const [isAddressModalOpen, setIsAddressModalOpen] = useState(false);
  const [editingAddress, setEditingAddress] = useState(null);
  const [addressFormData, setAddressFormData] = useState({
    shipToName: '',
    shipToPhone: '',
    shipZipcode: '',
    shipAddress1: '',
    shipAddress2: '',
    isDefault: false
  });
  const [isAddressSearchOpen, setIsAddressSearchOpen] = useState(false);
  const fetchData = async () => {
    try {
      const data = await getMyBodyInfoHistory();
      if (data && data.length > 0) {
        setHistoryData(data);
        // ✅ 백엔드에서 이미 최신순으로 정렬해서 반환하므로 첫 번째가 최신
        setLatestInfo(data[0]);
        // 배송지 목록도 함께 조회
        if (data[0]?.memberId) {
          fetchAddressList(data[0].memberId);
        }
      }
    } catch (error) {
      console.error("데이터 로딩 실패:", error);
    }
  };

  const fetchAddressList = async (memberId) => {
    try {
      const data = await getMemberInfoAddrList(memberId);
      // 기본 배송지가 맨 위로 오도록 정렬
      const sorted = [...data].sort((a, b) => {
        if (a.isDefault && !b.isDefault) return -1;
        if (!a.isDefault && b.isDefault) return 1;
        return 0;
      });
      setAddressList(sorted);
    } catch (error) {
      console.error("배송지 목록 조회 실패:", error);
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

  const safeParseFloat = (val) => {
    if (val === "" || val === null || val === undefined) return 0;
    const num = Number(val);
    return isNaN(num) ? 0 : num;
  };

  const handleSave = async (updatedData) => {
    try {
      const payload = {
        ...latestInfo,
        ...updatedData
      };

      // 불필요한 BaseEntity 필드 제거
      delete payload.regDate;
      delete payload.modDate;

      // 숫자로 변환
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

      await updateBodyInfo(payload.id, payload);

      alert("성공적으로 수정되었습니다.");
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error("수정 실패:", error);
      alert(error.message || "수정 중 오류가 발생했습니다.");
    }
  };

  // 차트 데이터 가공 - 날짜별 최신 1건만 남기고 표시 (같은 날 여러 기록 시 그래프 튐 방지)
  const getDateKey = (isoString) => {
    if (!isoString) return "";
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return "";
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  };

  const dailyLatestMap = new Map();
  for (const item of historyData) {
    const dateKey = getDateKey(item.measuredTime);
    if (!dateKey) continue;
    const current = dailyLatestMap.get(dateKey);
    const currentTime = current?.measuredTime ? new Date(current.measuredTime).getTime() : 0;
    const itemTime = item.measuredTime ? new Date(item.measuredTime).getTime() : 0;
    if (!current || itemTime >= currentTime) {
      dailyLatestMap.set(dateKey, item);
    }
  }

  const chartData = Array.from(dailyLatestMap.entries())
    .sort((a, b) => (a[0] > b[0] ? 1 : -1))
    .map(([dateKey, item]) => ({
      name: dateKey,
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

  // 배송지 관련 핸들러
  const handleAddAddressClick = () => {
    setEditingAddress(null);
    setAddressFormData({
      shipToName: '',
      shipToPhone: '',
      shipZipcode: '',
      shipAddress1: '',
      shipAddress2: '',
      isDefault: false
    });
    setIsAddressModalOpen(true);
  };

  const handleEditAddressClick = (address) => {
    setEditingAddress(address);
    setAddressFormData({
      shipToName: address.shipToName || '',
      shipToPhone: address.shipToPhone || '',
      shipZipcode: address.shipZipcode || '',
      shipAddress1: address.shipAddress1 || '',
      shipAddress2: address.shipAddress2 || '',
      isDefault: address.isDefault || false
    });
    setIsAddressModalOpen(true);
  };

  const handleAddressSave = async () => {
    try {
      if (editingAddress) {
        await updateMemberInfoAddr(editingAddress.id, addressFormData);
      } else {
        await createMemberInfoAddr(addressFormData);
      }
      setIsAddressModalOpen(false);
      if (latestInfo?.memberId) {
        fetchAddressList(latestInfo.memberId);
      }
      alert("배송지가 저장되었습니다.");
    } catch (error) {
      console.error("배송지 저장 실패:", error);
      alert(error.message || "배송지 저장 중 오류가 발생했습니다.");
    }
  };

  const handleDeleteAddress = async (id) => {
    if (!window.confirm("정말 삭제하시겠습니까?")) return;
    try {
      await deleteMemberInfoAddr(id);
      if (latestInfo?.memberId) {
        fetchAddressList(latestInfo.memberId);
      }
      alert("배송지가 삭제되었습니다.");
    } catch (error) {
      console.error("배송지 삭제 실패:", error);
      alert(error.message || "배송지 삭제 중 오류가 발생했습니다.");
    }
  };

  const handleSetDefaultAddress = async (id) => {
    try {
      await setDefaultMemberInfoAddr(id);
      if (latestInfo?.memberId) {
        fetchAddressList(latestInfo.memberId);
      }
      alert("기본 배송지로 설정되었습니다.");
    } catch (error) {
      console.error("기본 배송지 설정 실패:", error);
      alert(error.message || "기본 배송지 설정 중 오류가 발생했습니다.");
    }
  };

  return (
    <BasicLayout>
      <div className="dashboard-container" data-theme={isDark ? "dark" : "light"}>
        <header className="dashboard-header">
          <h1 className="section-title">
            <span className="text-text-main">My </span>
            <span className="text-primary-500">Profile</span>
            <Home className="icon-home" size={24} />
          </h1>
          <div className="header-right">
            <button className="btn-toggle-theme" onClick={toggleDarkMode}>
              {isDark ? <Sun size={20} /> : <Moon size={20} />}
            </button>
            <button type="button" className="btn-logout" onClick={handleLogout}>로그아웃</button>
          </div>
        </header>

        <div className="dashboard-main mt-6">
          {/* === 좌측 패널 === */}
          <aside className="left-sidebar">
            <div className="info-card">
              <div className="card-header">
                <h2 className="text-text-main font-display font-bold">회원정보</h2>
              </div>
              <div className="card-content profile-details">
                <div className="row name-row flex items-center justify-between gap-2 flex-wrap">
                  <div className="flex items-center gap-2">
                    <span className="name text-lg font-bold text-text-main">
                      {latestInfo?.memberName || "사용자"}
                    </span>
                    <span className="gender-icon text-sm text-text-sub">
                      {latestInfo?.gender === "MALE" ? "♂ 남성" : latestInfo?.gender === "FEMALE" ? "♀ 여성" : "-"}
                    </span>
                  </div>
                  <button type="button" className="btn-edit" onClick={handleEditClick}>
                    <User size={12} /> 수정
                  </button>
                </div>

                <div className="row date mt-1 text-text-sub text-sm">
                  {latestInfo?.birthDate} ({calculateAge(latestInfo?.birthDate)}세)
                </div>
                <div className="row stats text-text-main">
                  <span>{val(latestInfo?.height, "cm")}</span> &nbsp;/&nbsp; <span>{val(latestInfo?.weight, "kg")}</span>
                </div>
              </div>
            </div>

            <div className="info-card">
              <h3 className="section-title text-text-main">체성분 분석</h3>
              <div className="data-list">
                <DataRow label="체수분(L)" value={val(latestInfo?.bodyWater, "L")} />
                <DataRow label="단백질(kg)" value={val(latestInfo?.protein, "kg")} />
                <DataRow label="무기질(kg)" value={val(latestInfo?.minerals, "kg")} />
                <DataRow label="체지방(kg)" value={val(latestInfo?.bodyFatMass, "kg")} />
              </div>
            </div>

            <div className="info-card">
              <h3 className="section-title text-text-main">체중조절</h3>
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

        {/* ✅ 신체 정보 수정 모달 */}
        {isModalOpen && (
          <BodyInfoModifyModal
            data={editData}
            addressList={addressList}
            onClose={() => setIsModalOpen(false)}
            onSave={handleSave}
            onAddAddress={handleAddAddressClick}
            onEditAddress={handleEditAddressClick}
            onDeleteAddress={handleDeleteAddress}
            onSetDefaultAddress={handleSetDefaultAddress}
            onRefreshAddress={() => latestInfo?.memberId && fetchAddressList(latestInfo.memberId)}
          />
        )}

        {/* ✅ 배송지 추가/수정 모달 */}
        {isAddressModalOpen && (
          <AddressEditModal
            data={addressFormData}
            onChange={(field, value) => setAddressFormData(prev => ({ ...prev, [field]: value }))}
            onClose={() => setIsAddressModalOpen(false)}
            onSave={handleAddressSave}
            onAddressSearch={() => setIsAddressSearchOpen(true)}
          />
          )}

        {/* 주소 검색 모달 */}
        <AddressSearchModal
          isOpen={isAddressSearchOpen}
          onClose={() => setIsAddressSearchOpen(false)}
          onSelect={(addressData) => {
            setAddressFormData(prev => ({ ...prev, shipZipcode: addressData.zipcode, shipAddress1: addressData.address1, shipAddress2: addressData.address2 || prev.shipAddress2 }));
            setIsAddressSearchOpen(false);
          }}
        />
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

// ✅ 신체 정보 수정 모달 (배송지 관리 포함)
const BodyInfoModifyModal = ({ data, addressList, onClose, onSave, onAddAddress, onEditAddress, onDeleteAddress, onSetDefaultAddress, onRefreshAddress }) => {
  const [formData, setFormData] = useState({
    height: data?.height || '',
    weight: data?.weight || '',
    exercisePurpose: data?.exercisePurpose || ''
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value
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
        backgroundColor: 'white', padding: '30px', borderRadius: '10px', width: '600px',
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
            </div>
          </div>

          <div className="form-section">
            <h4 style={{borderBottom:'1px solid #ddd', paddingBottom:'5px', marginBottom:'10px', color: '#666'}}>운동 목적</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <label style={{ fontSize: '12px', color: '#666', fontWeight: 'bold' }}>운동 목적 선택</label>
              <select
                name="exercisePurpose"
                value={formData.exercisePurpose || ''}
                onChange={handleChange}
                style={{
                  padding: '8px',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '14px'
                }}
              >
                <option value="">선택해주세요</option>
                <option value="DIET">다이어트</option>
                <option value="MAINTAIN">유지</option>
                <option value="BULK_UP">벌크업</option>
              </select>
            </div>
          </div>

          <button type="submit" style={{
            marginTop: '10px', padding: '12px', backgroundColor: '#ccff00',
            border: 'none', borderRadius: '5px', fontWeight: 'bold', cursor: 'pointer', fontSize: '16px', color:'#000'
          }}>
            저장하기
          </button>
        </form>

        {/* 배송지 목록 섹션 */}
        <div style={{ marginTop: '30px', paddingTop: '20px', borderTop: '2px solid #ddd' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
            <h4 style={{ margin: 0, color: '#666' }}>배송지 목록</h4>
            <button
              type="button"
              onClick={onAddAddress}
              style={{
                padding: '6px 12px', backgroundColor: '#4A90E2', color: 'white',
                border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '14px',
                display: 'flex', alignItems: 'center', gap: '4px'
              }}
            >
              <Plus size={14} /> 추가
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {addressList && addressList.length > 0 ? (
              addressList.map((addr) => (
                <div
                  key={addr.id}
                  style={{
                    padding: '12px', border: '1px solid #ddd', borderRadius: '4px',
                    backgroundColor: addr.isDefault ? '#f0f8ff' : '#fff'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', marginBottom: '8px' }}>
                    <div style={{ flex: 1 }}>
                      {addr.isDefault && (
                        <span style={{ fontSize: '12px', color: '#4A90E2', fontWeight: 'bold', marginRight: '8px' }}>
                          [기본]
                        </span>
                      )}
                      <span style={{ fontWeight: '600' }}>{addr.shipToName}</span>
                      <span style={{ marginLeft: '8px', fontSize: '13px', color: '#666' }}>{addr.shipToPhone}</span>
                    </div>
                    <div style={{ display: 'flex', gap: '4px' }}>
                      {!addr.isDefault && (
                        <button
                          type="button"
                          onClick={() => onSetDefaultAddress(addr.id)}
                          style={{
                            padding: '4px 8px', fontSize: '11px', backgroundColor: '#f0f0f0',
                            border: '1px solid #ccc', borderRadius: '3px', cursor: 'pointer'
                          }}
                        >
                          기본설정
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => onEditAddress(addr)}
                        style={{
                          padding: '4px 8px', fontSize: '11px', backgroundColor: '#f0f0f0',
                          border: '1px solid #ccc', borderRadius: '3px', cursor: 'pointer'
                        }}
                      >
                        <Edit size={12} />
                      </button>
                      <button
                        type="button"
                        onClick={() => onDeleteAddress(addr.id)}
                        style={{
                          padding: '4px 8px', fontSize: '11px', backgroundColor: '#ffebee',
                          border: '1px solid #f44336', borderRadius: '3px', cursor: 'pointer'
                        }}
                      >
                        <Trash2 size={12} />
                      </button>
                    </div>
                  </div>
                  <div style={{ fontSize: '13px', color: '#666', lineHeight: '1.5' }}>
                    [{addr.shipZipcode}] {addr.shipAddress1} {addr.shipAddress2}
                  </div>
                </div>
              ))
            ) : (
              <div style={{ padding: '20px', textAlign: 'center', color: '#999', fontSize: '14px' }}>
                등록된 배송지가 없습니다.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

// ✅ 배송지 추가/수정 모달
const AddressEditModal = ({ data, onChange, onClose, onSave, onAddressSearch }) => {
  const handleChange = (e) => {
    const { name, value } = e.target;
    onChange(name, value);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSave();
  };

  return (
    <div className="modal-overlay" style={{
      position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
      backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 10000
    }}>
      <div className="modal-content" style={{
        backgroundColor: 'white', padding: '25px', borderRadius: '10px', width: '450px',
        position: 'relative', boxShadow: '0 4px 6px rgba(0,0,0,0.1)'
      }}>
        <button onClick={onClose} style={{ position: 'absolute', top: '15px', right: '15px', border: 'none', background: 'none', cursor: 'pointer' }}>
          <X size={24} />
        </button>

        <h3 style={{ marginBottom: '20px', textAlign: 'center', color: '#333' }}>배송지 {data.id ? '수정' : '추가'}</h3>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <InputGroup label="받는 분" name="shipToName" value={data.shipToName || ''} onChange={handleChange} />
          <InputGroup label="연락처" name="shipToPhone" value={data.shipToPhone || ''} onChange={handleChange} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
            <label style={{ fontSize: '12px', color: '#666', fontWeight:'bold' }}>우편번호</label>
            <div style={{ display: 'flex', gap: '8px' }}>
              <input
                type="text"
                name="shipZipcode"
                value={data.shipZipcode || ''}
                onChange={handleChange}
                style={{
                  flex: 1, padding: '8px', border: '1px solid #ddd', borderRadius: '4px',
                  fontSize:'14px'
                }}
                placeholder="우편번호"
              />
              <button
                type="button"
                onClick={onAddressSearch}
                style={{
                  padding: '8px 16px', backgroundColor: '#4A90E2', color: 'white',
                  border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '14px', whiteSpace: 'nowrap'
                }}
              >
                주소 검색
              </button>
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
            <label style={{ fontSize: '12px', color: '#666', fontWeight:'bold' }}>주소</label>
            <input
              type="text"
              name="shipAddress1"
              value={data.shipAddress1 || ''}
              onChange={handleChange}
              style={{
                padding: '8px', border: '1px solid #ddd', borderRadius: '4px',
                fontSize:'14px'
              }}
              placeholder="주소"
            />
          </div>
          <InputGroup label="상세주소" name="shipAddress2" value={data.shipAddress2 || ''} onChange={handleChange} />
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <input
              type="checkbox"
              id="isDefault"
              checked={data.isDefault || false}
              onChange={(e) => onChange('isDefault', e.target.checked)}
            />
            <label htmlFor="isDefault" style={{ fontSize: '14px', cursor: 'pointer' }}>
              기본 배송지로 설정
            </label>
          </div>

          <button type="submit" style={{
            marginTop: '10px', padding: '12px', backgroundColor: '#4A90E2',
            border: 'none', borderRadius: '5px', fontWeight: 'bold', cursor: 'pointer', fontSize: '16px', color: 'white'
          }}>
            저장
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
      type={name.includes('Name') || name.includes('Address') || name.includes('Phone') || name.includes('Zipcode') ? "text" : "number"}
      step="0.1"
      name={name}
      value={value !== null && value !== undefined ? value : ''}
      onChange={onChange}
      style={{ padding: '8px', border: '1px solid #ddd', borderRadius: '4px', fontSize:'14px' }}
    />
  </div>
);

export default ProfileIndex;
