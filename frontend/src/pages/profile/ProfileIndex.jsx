import React, { useState, useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";
// import { User, Moon, Sun, X, Plus, Edit, Trash2 } from "lucide-react";
import { Home, User, Moon, Sun, X, Plus, Edit, Trash2, ImagePlus, TrendingUp, TrendingDown } from "lucide-react";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Tooltip,
} from "recharts";
import { getMyBodyInfoHistory, updateBodyInfo, saveAndCompare } from "../../services/bodyInfoApi";
import {
  getMemberInfoAddrList,
  createMemberInfoAddr,
  updateMemberInfoAddr,
  deleteMemberInfoAddr,
  setDefaultMemberInfoAddr
} from "../../services/memberInfoAddrApi";
import { extractOcrText } from "../../services/ocrApi";

const ProfileIndex = () => {
  const location = useLocation();
  const navigate = useNavigate();
//   const [isDark, setIsDark] = useState(false);
//   const toggleDarkMode = () => setIsDark((prev) => !prev);

  const [historyData, setHistoryData] = useState([]);
  const [latestInfo, setLatestInfo] = useState(null);

  // 대시보드 차트/레이아웃에서 사용할 다크 모드 플래그
  const isDark = true;

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

  // OCR 관련 상태 (2칸 순서 업로드 → 처리 → 피드백 모달)
  const [isOcrModalOpen, setIsOcrModalOpen] = useState(false);
  const [ocrStep, setOcrStep] = useState("upload"); // 'upload' | 'processing' | 'confirm' | 'feedback'
  const [ocrImages, setOcrImages] = useState([]); // 최대 2개: [{ file, preview, text?, parsedData? }, ...]
  const [ocrLoading, setOcrLoading] = useState(false);
  const [ocrError, setOcrError] = useState(null);
  const [comparisonFeedback, setComparisonFeedback] = useState(null);
  // OCR 확인 단계: 편집 가능한 폼 값 & 측정일 (저장 시 이 값 사용)
  const [ocrConfirmForm, setOcrConfirmForm] = useState({});
  const [ocrMeasuredDate, setOcrMeasuredDate] = useState("");
  const ocrFileInputRef = useRef(null);
  const ocrSlotToFillRef = useRef(0);

  const fetchData = async () => {
    try {
      const data = await getMyBodyInfoHistory();
      if (data && data.length > 0) {
        setHistoryData(data);
        setLatestInfo(data[0]);
        // 배송지 목록도 함께 조회 (최신 신체정보 기준)
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

  // OCR: 인바디 자동분석 클릭 → 업로드 모달 오픈
  const handleOcrClick = () => {
    setOcrError(null);
    setOcrStep("upload");
    setOcrImages([]);
    setComparisonFeedback(null);
    setIsOcrModalOpen(true);
  };

  // OCR: 1번/2번 칸 클릭 시 해당 칸에 파일 선택
  const handleOcrSlotClick = (slotIndex) => {
    if (ocrLoading) return;
    ocrSlotToFillRef.current = slotIndex;
    ocrFileInputRef.current?.click();
  };

  // OCR: 파일 선택 후 한 장만 추가 (항상 0번 칸, OCR은 "분석 시작" 시 수행)
  const handleOcrFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = "";

    const allowedTypes = ["image/jpeg", "image/png", "image/gif", "image/webp"];
    if (!allowedTypes.includes(file.type)) {
      setOcrError("지원 형식: JPG, PNG, GIF, WEBP");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setOcrError("파일 크기는 10MB 이하여야 합니다.");
      return;
    }
    setOcrError(null);

    setOcrImages((prev) => {
      if (prev[0]?.preview) URL.revokeObjectURL(prev[0].preview);
      return [{ file, preview: URL.createObjectURL(file), text: null, parsedData: null }];
    });
  };

  // OCR: 이미지 제거 (한 장만 쓰므로 0번만)
  const handleOcrImageRemove = () => {
    setOcrImages((prev) => {
      if (prev[0]?.preview) URL.revokeObjectURL(prev[0].preview);
      return [];
    });
  };

  // OCR: 분석 시작 (1장 OCR → 파싱 → 분석 결과만 표시, DB 저장은 사용자 확인 후)
  const handleOcrProcess = async () => {
    const list = ocrImages.filter((item) => item?.file);
    if (list.length === 0) {
      setOcrError("이미지를 넣어주세요.");
      return;
    }
    setOcrError(null);
    setOcrStep("processing");
    setOcrLoading(true);

    try {
      const res = await extractOcrText(list[0].file);
      const parsedData = res?.parsed ?? {};
      setOcrImages((prev) => [{ ...prev[0], parsedData }]);
      setComparisonFeedback(null);
      // 확인 단계 진입 시 편집 폼·측정일 초기화 (OCR 추출값 + 기존 최신 정보 보조)
      const num = (v) => (v != null && v !== "" ? Number(v) : null);
      const field = (key) => num(parsedData?.[key]) ?? (latestInfo != null ? latestInfo[key] : null);
      setOcrConfirmForm({
        height: field("height") ?? "",
        weight: field("weight") ?? "",
        skeletalMuscleMass: field("skeletalMuscleMass") ?? "",
        bodyFatPercent: field("bodyFatPercent") ?? "",
        bodyWater: field("bodyWater") ?? "",
        protein: field("protein") ?? "",
        minerals: field("minerals") ?? "",
        bodyFatMass: field("bodyFatMass") ?? ""
      });
      const rawDate = parsedData?.measurementDate;
      const dateStr = rawDate
        ? (typeof rawDate === "string" ? rawDate.slice(0, 10) : "")
        : new Date().toISOString().slice(0, 10);
      setOcrMeasuredDate(dateStr || new Date().toISOString().slice(0, 10));
      setOcrStep("confirm");
    } catch (err) {
      setOcrError(err.message || "OCR 처리 중 오류가 발생했습니다.");
      setOcrStep("upload");
    } finally {
      setOcrLoading(false);
    }
  };

  // OCR: "저장할까요?" → [저장] 클릭 시 편집 폼 + 측정일 기준으로 DB 저장 후 비교 결과 표시
  const handleOcrConfirmSave = async () => {
    setOcrLoading(true);
    setOcrError(null);
    try {
      const payload = buildBodyInfoPayloadFromForm(ocrConfirmForm, ocrMeasuredDate, latestInfo);
      const feedback = await saveAndCompare(payload);
      setComparisonFeedback(feedback);
      setOcrStep("feedback");
      fetchData();
    } catch (err) {
      setOcrError(err.message || "저장 중 오류가 발생했습니다.");
    } finally {
      setOcrLoading(false);
    }
  };

  // OCR 확인 폼 + 측정일 → MemberInfoBodyDTO 형태로 변환 (빈 값은 최신 신체정보로 채움)
  const buildBodyInfoPayloadFromForm = (form, measuredDateStr, fallback = null) => {
    const num = (v) => (v != null && v !== "" ? Number(v) : null);
    const field = (key) => num(form?.[key]) ?? (fallback != null ? fallback[key] : null);
    const measuredTime = measuredDateStr
      ? new Date(measuredDateStr + "T12:00:00.000Z").toISOString()
      : new Date().toISOString();
    return {
      height: field("height"),
      weight: field("weight"),
      skeletalMuscleMass: field("skeletalMuscleMass"),
      bodyFatPercent: field("bodyFatPercent"),
      bodyWater: field("bodyWater"),
      protein: field("protein"),
      minerals: field("minerals"),
      bodyFatMass: field("bodyFatMass"),
      targetWeight: null,
      weightControl: null,
      fatControl: null,
      muscleControl: null,
      exercisePurpose: null,
      measuredTime,
    };
  };

  // OCR: 피드백 모달 닫기 및 초기화
  const handleOcrFeedbackClose = () => {
    ocrImages.forEach((item) => {
      if (item?.preview) URL.revokeObjectURL(item.preview);
    });
    setOcrImages([]);
    setOcrConfirmForm({});
    setOcrMeasuredDate("");
    setOcrStep("upload");
    setComparisonFeedback(null);
    setOcrError(null);
    setIsOcrModalOpen(false);
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

      // 운동 목적이 폼에서 선택된 값으로 랭킹 '내 그룹'에 반영되도록 확실히 설정
      if (updatedData.exercisePurpose !== undefined && updatedData.exercisePurpose !== '') {
        payload.exercisePurpose = updatedData.exercisePurpose;
      }

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

  // 차트 데이터 가공 - measuredTime을 X축에 표시 (오른쪽으로 갈수록 최근 날짜)
  const sortedHistory = [...historyData].sort((a, b) => {
    const aTime = a.measuredTime ? new Date(a.measuredTime).getTime() : 0;
    const bTime = b.measuredTime ? new Date(b.measuredTime).getTime() : 0;
    return aTime - bTime; // 오래된 날짜 -> 최신 날짜 순
  });

  // ✅ 가장 최근 3개의 측정값만 사용
  const recentHistory = sortedHistory.slice(-3);

  const chartData = recentHistory.map((item) => {
    // measuredTime을 보기 좋은 날짜 라벨로 변환 (예: 03-15)
    let name = "";
    if (item.measuredTime) {
      const date = new Date(item.measuredTime);
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      name = `${month}-${day}`;
    }
    return {
      name,
      fatRate: item.bodyFatPercent,
      muscle: item.skeletalMuscleMass,
      weight: item.weight,
    };
  });

  const val = (v, unit = "") => (v !== null && v !== undefined ? `${v} ${unit}` : "-");

  // 체중조절용: 수치가 항상 있으므로 null/undefined면 0으로 표시
  const valNum = (v, unit = "") => `${v != null && v !== "" ? Number(v) : 0} ${unit}`;
  // 조절 항목: 감소 시 -, 증가 시 +, 0이면 유지 (0 kg)
  const formatControl = (v, unit = "kg") => {
    const n = v != null && v !== "" ? Number(v) : 0;
    if (n === 0) return `유지 (0 ${unit})`;
    if (n > 0) return `+${n} ${unit}`;
    return `${n} ${unit}`;
  };

  // OCR로 저장된 기록일 때만 체중조절 수치·차트 표시 (프로필 수정/회원가입 직후는 "-")
  const showOcrControlValues = latestInfo?.dataSource === "OCR";

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
                <DataRow label="체지방(kg)" value={val(latestInfo?.bodyFatMass, "kg")} />
              </div>
            </div>

            <div className="info-card">
              <h3 className="section-title text-text-main">체중조절</h3>
              <div className="data-list">
                <DataRow label="적정체중" value={showOcrControlValues ? valNum(latestInfo.targetWeight, "kg") : "-"} />
                <DataRow label="체중조절" value={showOcrControlValues ? formatControl(latestInfo.weightControl) : "-"} />
                <DataRow label="지방조절" value={showOcrControlValues ? formatControl(latestInfo.fatControl) : "-"} />
                <DataRow label="근육조절" value={showOcrControlValues ? formatControl(latestInfo.muscleControl) : "-"} />
              </div>
            </div>
          </aside>

          {/* === 우측 패널 (차트) === */}
          <main className="right-content">
            <div className="badge-row">
              <span
                className="lime-badge"
                role="button"
                tabIndex={0}
                onClick={handleOcrClick}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleOcrClick(); } }}
                style={{ cursor: 'pointer' }}
              >
                인바디 자동분석
              </span>
            </div>
            <input
              ref={ocrFileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              onChange={handleOcrFileChange}
              style={{ display: 'none' }}
            />
            <div className="charts-container">
              <ChartRow title="체지방률" value={showOcrControlValues ? val(latestInfo?.bodyFatPercent, "%") : "-"}
                        chartTitle="체지방률 변화" data={showOcrControlValues ? chartData : []} dataKey="fatRate" strokeColor="#4A90E2" isDark={isDark} />
              <ChartRow title="골격근량" value={showOcrControlValues ? val(latestInfo?.skeletalMuscleMass, "kg") : "-"}
                        chartTitle="골격근량 변화" data={showOcrControlValues ? chartData : []} dataKey="muscle" strokeColor="#D0021B" isDark={isDark} />
              <ChartRow title="체중" value={showOcrControlValues ? val(latestInfo?.weight, "kg") : "-"}
                        chartTitle="체중 변화" data={showOcrControlValues ? chartData : []} dataKey="weight" strokeColor="#7ED321" isDark={isDark} />
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
          />
        )}

        {/* ✅ 인바디 자동분석 모달 (2칸 순서 업로드 → 처리 → 피드백) */}
        {isOcrModalOpen && (
          <div
            className="modal-overlay"
            style={{
              position: "fixed", top: 0, left: 0, width: "100%", height: "100%",
              backgroundColor: "rgba(0,0,0,0.5)", display: "flex", justifyContent: "center", alignItems: "center", zIndex: 9999
            }}
            onClick={() => !ocrLoading && handleOcrFeedbackClose()}
          >
            <div
              className="modal-content"
              style={{
                backgroundColor: "white", padding: "24px", borderRadius: "10px", width: "520px", maxWidth: "90vw",
                maxHeight: "85vh", overflowY: "auto", position: "relative", boxShadow: "0 4px 20px rgba(0,0,0,0.15)"
              }}
              onClick={(e) => e.stopPropagation()}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
                <h3 style={{ margin: 0, fontSize: "18px", color: "#333" }}>
                  {ocrStep === "upload" && "인바디 이미지 업로드"}
                  {ocrStep === "processing" && "분석 중"}
                  {ocrStep === "confirm" && "분석 결과"}
                  {ocrStep === "feedback" && "비교 분석 결과"}
                </h3>
                <button
                  type="button"
                  onClick={() => !ocrLoading && handleOcrFeedbackClose()}
                  style={{ border: "none", background: "none", cursor: "pointer", padding: "4px" }}
                >
                  <X size={22} />
                </button>
              </div>

              {ocrError && (
                <p style={{ color: "#c62828", margin: "0 0 12px", fontSize: "14px" }}>{ocrError}</p>
              )}

              {ocrStep === "upload" && (
                <>
                  <p style={{ color: "#666", fontSize: "13px", marginBottom: "12px" }}>
                    인바디 이미지 한 장을 넣어주세요. 이전 기록이 있으면 비교 분석합니다.
                  </p>
                  <div
                    style={{
                      border: "2px dashed #ccc", borderRadius: "8px", padding: "12px", textAlign: "center"
                    }}
                  >
                    {ocrImages[0] ? (
                      <>
                        <img
                          src={ocrImages[0].preview}
                          alt="인바디"
                          style={{ maxWidth: "100%", maxHeight: "160px", objectFit: "contain", borderRadius: "4px" }}
                        />
                        <button
                          type="button"
                          onClick={handleOcrImageRemove}
                          style={{
                            marginTop: "8px", padding: "4px 8px", fontSize: "12px", background: "#ffebee", color: "#c62828",
                            border: "none", borderRadius: "4px", cursor: "pointer"
                          }}
                        >
                          삭제
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleOcrSlotClick(0)}
                        style={{
                          width: "100%", minHeight: "120px", border: "none", background: "#f5f5f5", borderRadius: "6px",
                          cursor: "pointer", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: "8px"
                        }}
                      >
                        <ImagePlus size={32} color="#999" />
                        <span style={{ fontSize: "13px", color: "#666" }}>이미지 추가</span>
                      </button>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={handleOcrProcess}
                    disabled={!ocrImages[0]?.file}
                    style={{
                      marginTop: "16px", width: "100%", padding: "12px", backgroundColor: "#ccff00", color: "#000",
                      border: "none", borderRadius: "6px", fontWeight: "bold", cursor: "pointer", fontSize: "14px"
                    }}
                  >
                    분석 시작
                  </button>
                </>
              )}

              {ocrStep === "processing" && (
                <p style={{ color: "#666", margin: "20px 0" }}>이미지에서 텍스트를 추출하고 분석 중입니다...</p>
              )}

              {ocrStep === "confirm" && (
                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                  <p style={{ margin: 0, fontSize: "14px", color: "#333" }}>추출된 체성분 수치입니다. 필요하면 수정한 뒤 저장하세요.</p>
                  <div style={{ marginBottom: "8px" }}>
                    <label style={{ display: "block", fontSize: "12px", color: "#666", marginBottom: "4px" }}>측정일</label>
                    <input
                      type="date"
                      value={ocrMeasuredDate}
                      onChange={(e) => setOcrMeasuredDate(e.target.value)}
                      style={{
                        width: "100%", padding: "8px 10px", borderRadius: "6px", border: "1px solid #ccc",
                        fontSize: "14px", boxSizing: "border-box"
                      }}
                    />
                  </div>
                  <div style={{ background: "#f5f5f5", borderRadius: "8px", padding: "12px", fontSize: "13px", color: "#333" }}>
                    {[
                      { key: "체중", field: "weight", unit: "kg" },
                      { key: "키", field: "height", unit: "cm" },
                      { key: "골격근량", field: "skeletalMuscleMass", unit: "kg" },
                      { key: "체지방률", field: "bodyFatPercent", unit: "%" },
                      { key: "체수분", field: "bodyWater", unit: "L" },
                      { key: "단백질", field: "protein", unit: "kg" },
                      { key: "무기질", field: "minerals", unit: "kg" },
                      { key: "체지방량", field: "bodyFatMass", unit: "kg" }
                    ].map(({ key, field, unit }) => (
                      <div key={field} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "8px", gap: "8px" }}>
                        <span style={{ minWidth: "80px" }}>{key}</span>
                        <input
                          type="number"
                          step={field === "bodyFatPercent" ? "0.1" : "0.01"}
                          placeholder="—"
                          value={ocrConfirmForm[field] ?? ""}
                          onChange={(e) => setOcrConfirmForm((prev) => ({ ...prev, [field]: e.target.value }))}
                          style={{
                            flex: 1, maxWidth: "120px", padding: "6px 8px", borderRadius: "4px", border: "1px solid #ccc",
                            fontSize: "13px", textAlign: "right"
                          }}
                        />
                        <span style={{ width: "24px", textAlign: "left" }}>{unit}</span>
                      </div>
                    ))}
                  </div>
                  <div style={{ display: "flex", gap: "8px", marginTop: "8px" }}>
                    <button
                      type="button"
                      onClick={handleOcrConfirmSave}
                      disabled={ocrLoading}
                      style={{
                        flex: 1, padding: "10px 16px", backgroundColor: "#2e7d32", color: "#fff",
                        border: "none", borderRadius: "6px", fontWeight: "bold", cursor: ocrLoading ? "not-allowed" : "pointer", fontSize: "14px"
                      }}
                    >
                      {ocrLoading ? "저장 중..." : "저장"}
                    </button>
                    <button
                      type="button"
                      onClick={handleOcrFeedbackClose}
                      disabled={ocrLoading}
                      style={{
                        flex: 1, padding: "10px 16px", backgroundColor: "#757575", color: "#fff",
                        border: "none", borderRadius: "6px", fontWeight: "bold", cursor: ocrLoading ? "not-allowed" : "pointer", fontSize: "14px"
                      }}
                    >
                      아니요
                    </button>
                  </div>
                </div>
              )}

              {ocrStep === "feedback" && comparisonFeedback && (
                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                  <p style={{ margin: 0, fontSize: "14px", color: "#333" }}>{comparisonFeedback.summary}</p>
                  {comparisonFeedback.bodyChanges?.length > 0 && (
                    <div>
                      <h4 style={{ margin: "0 0 8px", fontSize: "14px", color: "#555" }}>체성분 변화</h4>
                      <ul style={{ margin: 0, paddingLeft: "20px" }}>
                        {comparisonFeedback.bodyChanges.map((item, i) => (
                          <li key={i} style={{ marginBottom: "4px", fontSize: "13px" }}>
                            {item.message}
                            {item.change === "증가" && <TrendingUp size={14} style={{ verticalAlign: "middle", color: "#c62828" }} />}
                            {item.change === "감소" && <TrendingDown size={14} style={{ verticalAlign: "middle", color: "#2e7d32" }} />}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {comparisonFeedback.mealFeedback && (
                    <p style={{ margin: 0, fontSize: "13px", color: "#555" }}><strong>식단:</strong> {comparisonFeedback.mealFeedback}</p>
                  )}
                  {comparisonFeedback.exerciseFeedback && (
                    <p style={{ margin: 0, fontSize: "13px", color: "#555" }}><strong>운동:</strong> {comparisonFeedback.exerciseFeedback}</p>
                  )}
                  {comparisonFeedback.recommendations?.length > 0 && (
                    <div>
                      <h4 style={{ margin: "0 0 8px", fontSize: "14px", color: "#555" }}>권장사항</h4>
                      <ul style={{ margin: 0, paddingLeft: "20px" }}>
                        {comparisonFeedback.recommendations.map((rec, i) => (
                          <li key={i} style={{ marginBottom: "4px", fontSize: "13px" }}>{rec}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  <button
                    type="button"
                    onClick={handleOcrFeedbackClose}
                    style={{
                      marginTop: "8px", padding: "10px 16px", backgroundColor: "#333", color: "#fff",
                      border: "none", borderRadius: "6px", fontWeight: "bold", cursor: "pointer", fontSize: "14px"
                    }}
                  >
                    닫기
                  </button>
                </div>
              )}
            </div>
          </div>
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
          formatter={(value, key) => {
            const num = Number(value);
            if (Number.isNaN(num)) {
              return ["-", key === "fatRate" ? "%" : "kg"];
            }
            if (key === "fatRate") {
              // 체지방률: 소수 2자리 + %
              return [`${num.toFixed(2)}`, "%"];
            }
            // 골격근량/체중 등: kg 단위, 소수 1자리
            return [`${num.toFixed(1)}`, "kg"];
          }}
        />
        <Line
          type="linear"
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
const AddressEditModal = ({ data, onChange, onClose, onSave }) => {
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
          <InputGroup label="우편번호" name="shipZipcode" value={data.shipZipcode || ''} onChange={handleChange} />
          <InputGroup label="주소" name="shipAddress1" value={data.shipAddress1 || ''} onChange={handleChange} />
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
