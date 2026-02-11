import React, { useState, useEffect } from "react";
import BasicLayout from "../../components/layout/BasicLayout";
import { User, Moon, Sun, X, Plus, Edit, Trash2 } from "lucide-react";
import { useTheme } from "../../contexts/ThemeContext";
import AddressSearchModal from "../../components/common/AddressSearchModal";
import OcrInbodyUploadModal from "../../components/profile/ocrInbodyUploadModal";
import OcrInbodyVerifyModal from "../../components/profile/OcrInbodyVerifyModal";
import { analyzeInbodyImage, saveVerifiedBodyInfo } from "../../services/ocrInbodyApi";
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
  const [historyData, setHistoryData] = useState([]);
  const [latestInfo, setLatestInfo] = useState(null);

  // 테마 가져오기
  const { theme } = useTheme();
  const isDark = theme === 'dark';

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editData, setEditData] = useState({});

  // 인바디 OCR 업로드 모달
  const [isInbodyOcrOpen, setIsInbodyOcrOpen] = useState(false);
  // 인바디 검증 모달
  const [isInbodyVerifyOpen, setIsInbodyVerifyOpen] = useState(false);
  const [inbodyVerifyData, setInbodyVerifyData] = useState(null);

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
        
        // 최신 정보 찾기: createdAt 또는 id 기준으로 가장 최근 것 선택
        // (회원정보 수정 레코드도 최신이면 선택되도록)
        const latest = data.reduce((prev, current) => {
          const prevCreated = prev?.createdAt ? new Date(prev.createdAt).getTime() : (prev?.id || 0);
          const currentCreated = current?.createdAt ? new Date(current.createdAt).getTime() : (current?.id || 0);
          return currentCreated > prevCreated ? current : prev;
        }, data[0]);
        
        setLatestInfo(latest);
        console.log("[ProfileIndex] 최신 정보 업데이트:", latest);
        
        // 배송지 목록도 함께 조회
        if (latest?.memberId) {
          fetchAddressList(latest.memberId);
        }
      } else {
        // 데이터가 없으면 초기화
        setHistoryData([]);
        setLatestInfo(null);
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
      // 회원정보 수정: 키, 몸무게, 운동목적만 저장
      // 건강정보 관련 컬럼들은 저장하지 않음 (OCR로 측정된 값만 그래프에 표시)
      const payload = {
        id: latestInfo?.id, // 기존 레코드 ID (업데이트용)
        height: Number(updatedData.height) || 0,
        weight: Number(updatedData.weight) || 0,
        exercisePurpose: updatedData.exercisePurpose || null,
        // measuredTime은 null로 설정 (OCR로 측정된 것이 아니므로 그래프에 포함되지 않음)
        measuredTime: null,
        // 건강정보 컬럼들은 명시적으로 제외 (null로 저장되지 않도록)
      };

      console.log("🚀 회원정보 수정 Payload:", payload);

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

  // 각 항목별로 별도의 차트 데이터 생성 (각 항목의 measured_time 기준 마지막 측정날짜까지만 표시)
  const chartDataFatRate = React.useMemo(() => {
    if (!historyData || historyData.length === 0) return [];
    
    const dailyLatestMap = new Map();
    for (const item of historyData) {
      // measured_time이 없으면 제외
      if (!item.measuredTime) continue;
      // bodyFatPercent가 null/undefined가 아닌 항목만 포함
      if (item.bodyFatPercent == null) continue;
      
      const dateKey = getDateKey(item.measuredTime);
      if (!dateKey) continue;
      const current = dailyLatestMap.get(dateKey);
      const currentTime = current?.measuredTime ? new Date(current.measuredTime).getTime() : 0;
      const itemTime = item.measuredTime ? new Date(item.measuredTime).getTime() : 0;
      
      // measuredTime 비교
      if (!current || itemTime > currentTime) {
        dailyLatestMap.set(dateKey, item);
      } else if (itemTime === currentTime) {
        // measuredTime이 같으면 createdAt으로 비교 (더 최신 것 선택)
        const currentCreated = current?.createdAt ? new Date(current.createdAt).getTime() : (current?.id || 0);
        const itemCreated = item?.createdAt ? new Date(item.createdAt).getTime() : (item?.id || 0);
        if (itemCreated > currentCreated) {
          dailyLatestMap.set(dateKey, item);
        }
      }
    }

    return Array.from(dailyLatestMap.entries())
      .sort((a, b) => (a[0] > b[0] ? 1 : -1))
      .map(([dateKey, item]) => ({
        name: dateKey,
        fatRate: item.bodyFatPercent,
      }));
  }, [historyData]);

  const chartDataMuscle = React.useMemo(() => {
    if (!historyData || historyData.length === 0) return [];
    
    const dailyLatestMap = new Map();
    for (const item of historyData) {
      // measured_time이 없으면 제외
      if (!item.measuredTime) continue;
      // skeletalMuscleMass가 null/undefined가 아닌 항목만 포함
      if (item.skeletalMuscleMass == null) continue;
      
      const dateKey = getDateKey(item.measuredTime);
      if (!dateKey) continue;
      const current = dailyLatestMap.get(dateKey);
      const currentTime = current?.measuredTime ? new Date(current.measuredTime).getTime() : 0;
      const itemTime = item.measuredTime ? new Date(item.measuredTime).getTime() : 0;
      
      // measuredTime 비교
      if (!current || itemTime > currentTime) {
        dailyLatestMap.set(dateKey, item);
      } else if (itemTime === currentTime) {
        // measuredTime이 같으면 createdAt으로 비교 (더 최신 것 선택)
        const currentCreated = current?.createdAt ? new Date(current.createdAt).getTime() : (current?.id || 0);
        const itemCreated = item?.createdAt ? new Date(item.createdAt).getTime() : (item?.id || 0);
        if (itemCreated > currentCreated) {
          dailyLatestMap.set(dateKey, item);
        }
      }
    }

    return Array.from(dailyLatestMap.entries())
      .sort((a, b) => (a[0] > b[0] ? 1 : -1))
      .map(([dateKey, item]) => ({
        name: dateKey,
        muscle: item.skeletalMuscleMass,
      }));
  }, [historyData]);

  const chartDataWeight = React.useMemo(() => {
    if (!historyData || historyData.length === 0) return [];
    
    const dailyLatestMap = new Map();
    for (const item of historyData) {
      // weight가 null/undefined가 아닌 항목만 포함
      if (item.weight == null) continue;
      
      // measuredTime이 있으면 measuredTime 기준으로 날짜 키 생성
      // measuredTime이 없으면 createdAt 기준으로 날짜 키 생성 (회원정보 수정 레코드)
      let dateKey = "";
      if (item.measuredTime) {
        dateKey = getDateKey(item.measuredTime);
      } else if (item.createdAt) {
        dateKey = getDateKey(item.createdAt);
      }
      
      if (!dateKey) continue;
      
      const current = dailyLatestMap.get(dateKey);
      
      if (!current) {
        dailyLatestMap.set(dateKey, item);
      } else {
        // 같은 날짜에 여러 레코드가 있으면 더 최신 것 선택
        const currentCreated = current?.createdAt ? new Date(current.createdAt).getTime() : (current?.id || 0);
        const itemCreated = item?.createdAt ? new Date(item.createdAt).getTime() : (item?.id || 0);
        if (itemCreated > currentCreated) {
          dailyLatestMap.set(dateKey, item);
        }
      }
    }

    return Array.from(dailyLatestMap.entries())
      .sort((a, b) => (a[0] > b[0] ? 1 : -1))
      .map(([dateKey, item]) => ({
        name: dateKey,
        weight: item.weight,
      }));
  }, [historyData]);

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

  const handleAnalyzeInbodyOcr = async (file) => {
    try {
      const result = await analyzeInbodyImage(file);
      // 업로드 모달 닫기 -> 검증 모달 열기
      setIsInbodyOcrOpen(false);
      setInbodyVerifyData(result);
      setIsInbodyVerifyOpen(true);
    } catch (err) {
      console.error(err);
      alert("분석에 실패했습니다.");
    }
  };

  const handleSaveVerifiedInbody = async (finalData) => {
    console.log("[ProfileIndex] 저장 시작 - finalData:", finalData);
    try {
      const result = await saveVerifiedBodyInfo(finalData);
      console.log("[ProfileIndex] 저장 성공 - result:", result);
      
      // 모달 먼저 닫기
      setIsInbodyVerifyOpen(false);
      
      // 데이터 즉시 갱신 (새로고침 불필요)
      await fetchData();
      
      // 성공 메시지 (데이터 갱신 후 표시)
      alert("인바디 정보가 저장되었습니다.");
    } catch (err) {
      console.error("[ProfileIndex] 저장 실패:", err);
      console.error("[ProfileIndex] 에러 상세:", err.response?.data || err.message);
      alert(`저장에 실패했습니다: ${err.response?.data?.message || err.message || "알 수 없는 오류"}`);
    }
  };

  return (
    <BasicLayout containerClassName="page-container dashboard-container">
      <div className="w-full">
        <header className="section-header-token">
          <h1 className="section-title">
            <span className="text-text-main">My </span>
            <span className="text-primary-500">Profile</span>
          </h1>
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
                <DataRow label="체지방량(kg)" value={val(latestInfo?.bodyFatMass, "kg")} />
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
              <button
                type="button"
                className="lime-badge"
                onClick={() => setIsInbodyOcrOpen(true)}
              >
                인바디 자동분석
              </button>
            </div>
            <div className="charts-container">
              <ChartRow title="체지방률" value={val(latestInfo?.bodyFatPercent, "%")}
                        chartTitle="체지방률 변화" data={chartDataFatRate} dataKey="fatRate" strokeColor="#4A90E2" isDark={isDark} />
              <ChartRow title="골격근량" value={val(latestInfo?.skeletalMuscleMass, "kg")}
                        chartTitle="골격근량 변화" data={chartDataMuscle} dataKey="muscle" strokeColor="#D0021B" isDark={isDark} />
              <ChartRow title="체중" value={val(latestInfo?.weight, "kg")}
                        chartTitle="체중 변화" data={chartDataWeight} dataKey="weight" strokeColor="#7ED321" isDark={isDark} />
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
            isDark={isDark}
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
            isDark={isDark}
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

        {/* ✅ 인바디 OCR 업로드 모달 */}
        <OcrInbodyUploadModal
          isOpen={isInbodyOcrOpen}
          onClose={() => setIsInbodyOcrOpen(false)}
          onAnalyze={handleAnalyzeInbodyOcr}
          isDark={isDark}
        />

        {/* ✅ 인바디 OCR 검증 모달 */}
        <OcrInbodyVerifyModal
          isOpen={isInbodyVerifyOpen}
          data={inbodyVerifyData}
          onClose={() => setIsInbodyVerifyOpen(false)}
          onSave={handleSaveVerifiedInbody}
          isDark={isDark}
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
const BodyInfoModifyModal = ({ data, addressList, onClose, onSave, onAddAddress, onEditAddress, onDeleteAddress, onSetDefaultAddress, onRefreshAddress, isDark = true }) => {
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

  // 테마별 스타일
  const overlayBg = isDark ? "rgba(0,0,0,0.7)" : "rgba(0,0,0,0.4)";
  const modalBg = isDark ? "#1a1a1a" : "#ffffff";
  const modalColor = isDark ? "#fff" : "#000";
  const modalShadow = isDark ? "0 10px 40px rgba(0,0,0,0.5)" : "0 10px 40px rgba(0,0,0,0.15)";
  const closeBtnColor = isDark ? "#666" : "#999";
  const borderColor = isDark ? "#333" : "#e0e0e0";
  const sectionTitleColor = isDark ? "#aaa" : "#666";
  const labelColor = isDark ? "#aaa" : "#666";
  const inputBg = isDark ? "#222" : "#f5f5f5";
  const inputBorder = isDark ? "#444" : "#ddd";
  const inputColor = isDark ? "#fff" : "#000";
  const selectBg = isDark ? "#222" : "#fff";
  const selectBorder = isDark ? "#444" : "#ddd";
  const selectColor = isDark ? "#fff" : "#000";
  const optionBg = isDark ? "#222" : "#fff";
  const optionColor = isDark ? "#fff" : "#000";
  const addressItemBorder = isDark ? "#444" : "#ddd";
  const addressItemColor = isDark ? "#fff" : "#000";
  const addressTextColor = isDark ? "#aaa" : "#666";
  const emptyTextColor = isDark ? "#888" : "#999";
  const btnDefaultBg = isDark ? "#333" : "#e0e0e0";
  const btnDefaultColor = isDark ? "#ccc" : "#666";
  const btnDefaultBorder = isDark ? "#444" : "#ddd";
  const btnDeleteBg = isDark ? "#4a1a1a" : "#ffe0e0";
  const btnDeleteColor = isDark ? "#ff6b6b" : "#d32f2f";
  const btnDeleteBorder = isDark ? "#ff4444" : "#f44336";

  return (
    <div className="modal-overlay" style={{
      position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
      backgroundColor: overlayBg, display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 9999
    }}>
      <div className="modal-content" style={{
        backgroundColor: modalBg, padding: '30px', borderRadius: '12px', width: '600px',
        maxHeight: '90vh', overflowY: 'auto', position: 'relative', boxShadow: modalShadow,
        color: modalColor
      }}>
        <button onClick={onClose} style={{ position: 'absolute', top: '15px', right: '15px', border: 'none', background: 'none', cursor: 'pointer', color: closeBtnColor }}>
          <X size={24} />
        </button>

        <h2 style={{ marginBottom: '20px', textAlign: 'center', color: modalColor }}>신체 정보 수정</h2>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
          <div className="form-section">
            <h4 style={{borderBottom: `1px solid ${borderColor}`, paddingBottom:'5px', marginBottom:'10px', color: sectionTitleColor}}>기본 정보</h4>
            <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:'10px'}}>
              <InputGroup label="키 (cm)" name="height" value={formData.height} onChange={handleChange} isDark={isDark} />
              <InputGroup label="몸무게 (kg)" name="weight" value={formData.weight} onChange={handleChange} isDark={isDark} />
            </div>
          </div>

          <div className="form-section">
            <h4 style={{borderBottom: `1px solid ${borderColor}`, paddingBottom:'5px', marginBottom:'10px', color: sectionTitleColor}}>운동 목적</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <label style={{ fontSize: '12px', color: labelColor, fontWeight: 'bold' }}>운동 목적 선택</label>
              <select
                name="exercisePurpose"
                value={formData.exercisePurpose || ''}
                onChange={handleChange}
                style={{
                  padding: '8px',
                  border: `1px solid ${selectBorder}`,
                  borderRadius: '4px',
                  fontSize: '14px',
                  color: selectColor,
                  backgroundColor: selectBg
                }}
              >
                <option value="" style={{ backgroundColor: optionBg, color: optionColor }}>선택해주세요</option>
                <option value="DIET" style={{ backgroundColor: optionBg, color: optionColor }}>다이어트</option>
                <option value="MAINTAIN" style={{ backgroundColor: optionBg, color: optionColor }}>유지</option>
                <option value="BULK_UP" style={{ backgroundColor: optionBg, color: optionColor }}>벌크업</option>
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
        <div style={{ marginTop: '30px', paddingTop: '20px', borderTop: `1px solid ${borderColor}` }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
            <h4 style={{ margin: 0, color: sectionTitleColor }}>배송지 목록</h4>
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
                    padding: '12px', border: `1px solid ${addressItemBorder}`, borderRadius: '4px',
                    backgroundColor: isDark ? (addr.isDefault ? '#2a2a2a' : '#222') : (addr.isDefault ? '#f0f0f0' : '#fff'),
                    color: addressItemColor
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', marginBottom: '8px' }}>
                    <div style={{ flex: 1 }}>
                      {addr.isDefault && (
                        <span style={{ fontSize: '12px', color: '#4A90E2', fontWeight: 'bold', marginRight: '8px' }}>
                          [기본]
                        </span>
                      )}
                      <span style={{ fontWeight: '600', color: addressItemColor }}>{addr.shipToName}</span>
                      <span style={{ marginLeft: '8px', fontSize: '13px', color: addressTextColor }}>{addr.shipToPhone}</span>
                    </div>
                    <div style={{ display: 'flex', gap: '4px' }}>
                      {!addr.isDefault && (
                        <button
                          type="button"
                          onClick={() => onSetDefaultAddress(addr.id)}
                          style={{
                            padding: '4px 8px', fontSize: '11px', backgroundColor: btnDefaultBg, color: btnDefaultColor,
                            border: `1px solid ${btnDefaultBorder}`, borderRadius: '3px', cursor: 'pointer'
                          }}
                        >
                          기본설정
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => onEditAddress(addr)}
                        style={{
                          padding: '4px 8px', fontSize: '11px', backgroundColor: btnDefaultBg, color: btnDefaultColor,
                          border: `1px solid ${btnDefaultBorder}`, borderRadius: '3px', cursor: 'pointer'
                        }}
                      >
                        <Edit size={12} />
                      </button>
                      <button
                        type="button"
                        onClick={() => onDeleteAddress(addr.id)}
                        style={{
                          padding: '4px 8px', fontSize: '11px', backgroundColor: btnDeleteBg, color: btnDeleteColor,
                          border: `1px solid ${btnDeleteBorder}`, borderRadius: '3px', cursor: 'pointer'
                        }}
                      >
                        <Trash2 size={12} />
                      </button>
                    </div>
                  </div>
                  <div style={{ fontSize: '13px', color: addressTextColor, lineHeight: '1.5' }}>
                    [{addr.shipZipcode}] {addr.shipAddress1} {addr.shipAddress2}
                  </div>
                </div>
              ))
            ) : (
              <div style={{ padding: '20px', textAlign: 'center', color: emptyTextColor, fontSize: '14px' }}>
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
const AddressEditModal = ({ data, onChange, onClose, onSave, onAddressSearch, isDark = true }) => {
  const handleChange = (e) => {
    const { name, value } = e.target;
    onChange(name, value);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSave();
  };

  // 테마별 스타일
  const overlayBg = isDark ? "rgba(0,0,0,0.7)" : "rgba(0,0,0,0.4)";
  const modalBg = isDark ? "#1a1a1a" : "#ffffff";
  const modalColor = isDark ? "#fff" : "#000";
  const modalShadow = isDark ? "0 10px 40px rgba(0,0,0,0.5)" : "0 10px 40px rgba(0,0,0,0.15)";
  const closeBtnColor = isDark ? "#666" : "#999";
  const labelColor = isDark ? "#aaa" : "#666";
  const inputBg = isDark ? "#222" : "#f5f5f5";
  const inputBorder = isDark ? "#444" : "#ddd";
  const inputColor = isDark ? "#fff" : "#000";
  const checkboxLabelColor = isDark ? "#fff" : "#000";

  return (
    <div className="modal-overlay" style={{
      position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
      backgroundColor: overlayBg, display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 10000
    }}>
      <div className="modal-content" style={{
        backgroundColor: modalBg, padding: '25px', borderRadius: '12px', width: '450px',
        position: 'relative', boxShadow: modalShadow,
        color: modalColor
      }}>
        <button onClick={onClose} style={{ position: 'absolute', top: '15px', right: '15px', border: 'none', background: 'none', cursor: 'pointer', color: closeBtnColor }}>
          <X size={24} />
        </button>

        <h3 style={{ marginBottom: '20px', textAlign: 'center', color: modalColor }}>배송지 {data.id ? '수정' : '추가'}</h3>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <InputGroup label="받는 분" name="shipToName" value={data.shipToName || ''} onChange={handleChange} isDark={isDark} />
          <InputGroup label="연락처" name="shipToPhone" value={data.shipToPhone || ''} onChange={handleChange} isDark={isDark} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
            <label style={{ fontSize: '12px', color: labelColor, fontWeight:'bold' }}>우편번호</label>
            <div style={{ display: 'flex', gap: '8px' }}>
              <input
                type="text"
                name="shipZipcode"
                value={data.shipZipcode || ''}
                onChange={handleChange}
                style={{
                  flex: 1, padding: '8px', border: `1px solid ${inputBorder}`, borderRadius: '4px',
                  fontSize:'14px',
                  color: inputColor, backgroundColor: inputBg
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
            <label style={{ fontSize: '12px', color: labelColor, fontWeight:'bold' }}>주소</label>
            <input
              type="text"
              name="shipAddress1"
              value={data.shipAddress1 || ''}
              onChange={handleChange}
              style={{
                padding: '8px', border: `1px solid ${inputBorder}`, borderRadius: '4px',
                fontSize:'14px',
                color: inputColor, backgroundColor: inputBg
              }}
              placeholder="주소"
            />
          </div>
          <InputGroup label="상세주소" name="shipAddress2" value={data.shipAddress2 || ''} onChange={handleChange} isDark={isDark} />
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <input
              type="checkbox"
              id="isDefault"
              checked={data.isDefault || false}
              onChange={(e) => onChange('isDefault', e.target.checked)}
              style={{
                accentColor: '#ccff00',
                cursor: 'pointer'
              }}
            />
            <label htmlFor="isDefault" style={{ fontSize: '14px', cursor: 'pointer', color: checkboxLabelColor }}>
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

const InputGroup = ({ label, name, value, onChange, isDark = true }) => {
  const labelColor = isDark ? "#aaa" : "#666";
  const inputBg = isDark ? "#222" : "#f5f5f5";
  const inputBorder = isDark ? "#444" : "#ddd";
  const inputColor = isDark ? "#fff" : "#000";

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
      <label style={{ fontSize: '12px', color: labelColor, fontWeight:'bold' }}>{label}</label>
      <input
        type={name.includes('Name') || name.includes('Address') || name.includes('Phone') || name.includes('Zipcode') ? "text" : "number"}
        step="0.1"
        name={name}
        value={value !== null && value !== undefined ? value : ''}
        onChange={onChange}
        style={{ padding: '8px', border: `1px solid ${inputBorder}`, borderRadius: '4px', fontSize:'14px', color: inputColor, backgroundColor: inputBg }}
      />
    </div>
  );
};

export default ProfileIndex;
