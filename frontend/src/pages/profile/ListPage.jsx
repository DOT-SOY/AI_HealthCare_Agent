import React, { useState, useEffect } from "react";
import { User, X, Plus, Edit, Trash2 } from "lucide-react";
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

const ProfileList = () => {
  const [historyData, setHistoryData] = useState([]);
  const [latestInfo, setLatestInfo] = useState(null);

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
        setLatestInfo(data[0]);
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

  const handleSave = async (updatedData) => {
    try {
      const payload = {
        ...latestInfo,
        ...updatedData
      };

      delete payload.regDate;
      delete payload.modDate;

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

      await updateBodyInfo(payload.id, payload);

      alert("성공적으로 수정되었습니다.");
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error("수정 실패:", error);
      alert(error.message || "수정 중 오류가 발생했습니다.");
    }
  };

  // 차트 데이터 가공
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
    setAddressFormData({ shipToName: '', shipToPhone: '', shipZipcode: '', shipAddress1: '', shipAddress2: '', isDefault: false });
    setIsAddressModalOpen(true);
  };

  const handleEditAddressClick = (address) => {
    setEditingAddress(address);
    setAddressFormData({
      shipToName: address.shipToName || '', shipToPhone: address.shipToPhone || '',
      shipZipcode: address.shipZipcode || '', shipAddress1: address.shipAddress1 || '',
      shipAddress2: address.shipAddress2 || '', isDefault: address.isDefault || false
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
      if (latestInfo?.memberId) fetchAddressList(latestInfo.memberId);
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
      if (latestInfo?.memberId) fetchAddressList(latestInfo.memberId);
      alert("배송지가 삭제되었습니다.");
    } catch (error) {
      console.error("배송지 삭제 실패:", error);
      alert(error.message || "배송지 삭제 중 오류가 발생했습니다.");
    }
  };

  const handleSetDefaultAddress = async (id) => {
    try {
      await setDefaultMemberInfoAddr(id);
      if (latestInfo?.memberId) fetchAddressList(latestInfo.memberId);
      alert("기본 배송지로 설정되었습니다.");
    } catch (error) {
      console.error("기본 배송지 설정 실패:", error);
      alert(error.message || "기본 배송지 설정 중 오류가 발생했습니다.");
    }
  };

  return (
    <div className="w-full">
      {/* 헤더 — 다른 페이지와 동일한 section-header-token 패턴 */}
      <header className="section-header-token flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="section-title">
            <span className="text-text-main">My </span>
            <span className="text-primary-500">Profile</span>
          </h1>
          <p className="section-desc mt-1">회원 정보 및 체성분 분석</p>
        </div>
        <button type="button" className="segment-btn" onClick={handleEditClick}>
          <User size={16} /> 정보 수정
        </button>
      </header>

      {/* 2열 그리드: 좌측 정보 / 우측 차트 */}
      <div className="grid grid-cols-1 lg:grid-cols-[320px_1fr] gap-8">
        {/* === 좌측 패널 === */}
        <aside className="flex flex-col gap-6">
          <div className="card-token rounded-token p-5 border border-border-default">
            <h2 className="text-lg font-display font-bold text-text-main mb-3">회원정보</h2>
            <div className="flex items-center justify-between gap-2 flex-wrap mb-2">
              <div className="flex items-center gap-2">
                <span className="text-lg font-bold text-text-main">{latestInfo?.memberName || "사용자"}</span>
                <span className="text-sm text-text-sub">
                  {latestInfo?.gender === "MALE" ? "♂ 남성" : latestInfo?.gender === "FEMALE" ? "♀ 여성" : "-"}
                </span>
              </div>
            </div>
            <div className="text-text-sub text-sm mb-1">{latestInfo?.birthDate} ({calculateAge(latestInfo?.birthDate)}세)</div>
            <div className="text-text-main">
              <span>{val(latestInfo?.height, "cm")}</span> &nbsp;/&nbsp; <span>{val(latestInfo?.weight, "kg")}</span>
            </div>
          </div>

          <div className="card-token rounded-token p-5 border border-border-default">
            <h3 className="text-base font-display font-semibold text-text-main mb-3">체성분 분석</h3>
            <div className="space-y-2">
              <DataRow label="체수분(L)" value={val(latestInfo?.bodyWater, "L")} />
              <DataRow label="단백질(kg)" value={val(latestInfo?.protein, "kg")} />
              <DataRow label="무기질(kg)" value={val(latestInfo?.minerals, "kg")} />
              <DataRow label="체지방(kg)" value={val(latestInfo?.bodyFatMass, "kg")} />
            </div>
          </div>

          <div className="card-token rounded-token p-5 border border-border-default">
            <h3 className="text-base font-display font-semibold text-text-main mb-3">체중조절</h3>
            <div className="space-y-2">
              <DataRow label="적정체중" value={val(latestInfo?.targetWeight, "kg")} />
              <DataRow label="체중조절" value={val(latestInfo?.weightControl, "kg")} />
              <DataRow label="지방조절" value={val(latestInfo?.fatControl, "kg")} />
              <DataRow label="근육조절" value={val(latestInfo?.muscleControl, "kg")} />
            </div>
          </div>
        </aside>

        {/* === 우측 패널 (차트) === */}
        <main className="flex flex-col gap-6">
          <div className="flex items-center">
            <span className="text-xs font-bold px-3 py-1 rounded-token-sm bg-primary-500 text-bg-root uppercase tracking-wider">인바디 자동분석</span>
          </div>
          <div className="space-y-6">
            <ChartRow title="체지방률" value={val(latestInfo?.bodyFatPercent, "%")}
                      chartTitle="체지방률 변화" data={chartData} dataKey="fatRate" strokeColor="#4A90E2" />
            <ChartRow title="골격근량" value={val(latestInfo?.skeletalMuscleMass, "kg")}
                      chartTitle="골격근량 변화" data={chartData} dataKey="muscle" strokeColor="#D0021B" />
            <ChartRow title="체중" value={val(latestInfo?.weight, "kg")}
                      chartTitle="체중 변화" data={chartData} dataKey="weight" strokeColor="#7ED321" />
          </div>
        </main>
      </div>

      {/* 신체 정보 수정 모달 */}
      {isModalOpen && (
        <BodyInfoModifyModal
          data={editData} addressList={addressList}
          onClose={() => setIsModalOpen(false)} onSave={handleSave}
          onAddAddress={handleAddAddressClick} onEditAddress={handleEditAddressClick}
          onDeleteAddress={handleDeleteAddress} onSetDefaultAddress={handleSetDefaultAddress}
          onRefreshAddress={() => latestInfo?.memberId && fetchAddressList(latestInfo.memberId)}
        />
      )}

      {/* 배송지 추가/수정 모달 */}
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
  );
};

// --- Helper Components ---

function DataRow({ label, value }) {
  return (
    <div className="flex items-center justify-between py-1.5 border-b border-border-default last:border-0">
      <span className="text-sm text-text-sub">{label}</span>
      <span className="text-sm font-medium text-text-main">{value}</span>
    </div>
  );
}

function ChartRow({ title, value, chartTitle, data, dataKey, strokeColor }) {
  return (
    <div className="card-token rounded-token p-5 border border-border-default flex flex-col sm:flex-row gap-4">
      <div className="sm:w-28 flex-shrink-0 flex flex-col justify-center">
        <div className="text-sm text-text-sub">{title}</div>
        <div className="text-xl font-bold text-text-main">{value}</div>
      </div>
      <div className="flex-1">
        <div className="text-sm text-text-muted mb-2">{chartTitle}</div>
        <div className="w-full" style={{ height: "160px" }}>
          <SimpleLineChart data={data} dataKey={dataKey} stroke={strokeColor} />
        </div>
      </div>
    </div>
  );
}

function SimpleLineChart({ data, dataKey, stroke }) {
  if (!data || data.length === 0) {
    return (
      <div className="h-full flex items-center justify-center text-text-muted text-sm">
        데이터가 없습니다.
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="var(--gray-200)" strokeDasharray="3 3" />
        <XAxis dataKey="name" tickLine={false} axisLine={{ stroke: 'var(--gray-200)' }} tick={{ fontSize: 12, fill: 'var(--text-sub)' }} interval="preserveStartEnd" />
        <YAxis hide={false} tick={{ fontSize: 12, fill: 'var(--text-sub)' }} axisLine={false} tickLine={false} domain={['auto', 'auto']} width={40} />
        <Tooltip
          contentStyle={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-default)', color: 'var(--text-main)' }}
          formatter={(value) => [value, dataKey === "fatRate" ? "%" : "kg"]}
        />
        <Line type="monotone" dataKey={dataKey} stroke={stroke} strokeWidth={3} dot={{ r: 4, fill: stroke, strokeWidth: 0 }} activeDot={{ r: 6 }} isAnimationActive={true} />
      </LineChart>
    </ResponsiveContainer>
  );
}

// 신체 정보 수정 모달 (배송지 관리 포함)
const BodyInfoModifyModal = ({ data, addressList, onClose, onSave, onAddAddress, onEditAddress, onDeleteAddress, onSetDefaultAddress }) => {
  const [formData, setFormData] = useState({
    height: data?.height || '',
    weight: data?.weight || '',
    exercisePurpose: data?.exercisePurpose || ''
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSave(formData);
  };

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-bg-card text-text-main rounded-token p-6 w-full max-w-lg max-h-[90vh] overflow-y-auto relative border border-border-default shadow-card">
        <button onClick={onClose} className="absolute top-4 right-4 text-text-muted hover:text-text-main transition-colors" aria-label="닫기">
          <X size={24} />
        </button>

        <h2 className="mb-5 text-center text-xl font-bold text-text-main">신체 정보 수정</h2>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <h4 className="border-b border-border-default pb-1.5 mb-3 text-text-sub text-sm font-semibold">기본 정보</h4>
            <div className="grid grid-cols-2 gap-3">
              <InputGroup label="키 (cm)" name="height" value={formData.height} onChange={handleChange} />
              <InputGroup label="몸무게 (kg)" name="weight" value={formData.weight} onChange={handleChange} />
            </div>
          </div>

          <div>
            <h4 className="border-b border-border-default pb-1.5 mb-3 text-text-sub text-sm font-semibold">운동 목적</h4>
            <div className="flex flex-col gap-2">
              <label className="text-xs text-text-sub font-bold">운동 목적 선택</label>
              <select
                name="exercisePurpose"
                value={formData.exercisePurpose || ''}
                onChange={handleChange}
                className="select-token"
              >
                <option value="">선택해주세요</option>
                <option value="DIET">다이어트</option>
                <option value="MAINTAIN">유지</option>
                <option value="BULK_UP">벌크업</option>
              </select>
            </div>
          </div>

          <button type="submit" className="mt-2 py-3 bg-primary-500 text-bg-root rounded-token font-bold text-base hover:shadow-glow transition-all">
            저장하기
          </button>
        </form>

        {/* 배송지 목록 섹션 */}
        <div className="mt-8 pt-5 border-t-2 border-border-default">
          <div className="flex justify-between items-center mb-4">
            <h4 className="text-text-sub font-semibold text-sm">배송지 목록</h4>
            <button
              type="button"
              onClick={onAddAddress}
              className="px-3 py-1.5 bg-primary-500 text-bg-root rounded-token text-sm font-medium flex items-center gap-1 hover:shadow-glow-sm transition-all"
            >
              <Plus size={14} /> 추가
            </button>
          </div>

          <div className="flex flex-col gap-3">
            {addressList && addressList.length > 0 ? (
              addressList.map((addr) => (
                <div
                  key={addr.id}
                  className={`p-3 border rounded-token ${addr.isDefault ? 'border-primary-500/40 bg-primary-500/5' : 'border-border-default bg-bg-surface'}`}
                >
                  <div className="flex justify-between items-start mb-2">
                    <div className="flex-1">
                      {addr.isDefault && (
                        <span className="text-xs text-primary-500 font-bold mr-2">[기본]</span>
                      )}
                      <span className="font-semibold text-text-main">{addr.shipToName}</span>
                      <span className="ml-2 text-sm text-text-sub">{addr.shipToPhone}</span>
                    </div>
                    <div className="flex gap-1">
                      {!addr.isDefault && (
                        <button
                          type="button"
                          onClick={() => onSetDefaultAddress(addr.id)}
                          className="px-2 py-1 text-xs bg-bg-card border border-border-default rounded-token-sm hover:border-primary-500 hover:text-primary-500 transition-colors"
                        >
                          기본설정
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => onEditAddress(addr)}
                        className="px-2 py-1 text-xs bg-bg-card border border-border-default rounded-token-sm hover:border-primary-500 hover:text-primary-500 transition-colors"
                      >
                        <Edit size={12} />
                      </button>
                      <button
                        type="button"
                        onClick={() => onDeleteAddress(addr.id)}
                        className="px-2 py-1 text-xs bg-accent-secondary/10 border border-accent-secondary/30 rounded-token-sm text-accent-secondary hover:bg-accent-secondary/20 transition-colors"
                      >
                        <Trash2 size={12} />
                      </button>
                    </div>
                  </div>
                  <div className="text-sm text-text-sub leading-relaxed">
                    [{addr.shipZipcode}] {addr.shipAddress1} {addr.shipAddress2}
                  </div>
                </div>
              ))
            ) : (
              <div className="py-5 text-center text-text-muted text-sm">
                등록된 배송지가 없습니다.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

// 배송지 추가/수정 모달
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
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-[60]">
      <div className="bg-bg-card text-text-main rounded-token p-6 w-full max-w-md relative border border-border-default shadow-card">
        <button onClick={onClose} className="absolute top-4 right-4 text-text-muted hover:text-text-main transition-colors" aria-label="닫기">
          <X size={24} />
        </button>

        <h3 className="mb-5 text-center text-lg font-bold text-text-main">배송지 {data.id ? '수정' : '추가'}</h3>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <InputGroup label="받는 분" name="shipToName" value={data.shipToName || ''} onChange={handleChange} />
          <InputGroup label="연락처" name="shipToPhone" value={data.shipToPhone || ''} onChange={handleChange} />
          <div className="flex flex-col gap-1.5">
            <label className="text-xs text-text-sub font-bold">우편번호</label>
            <div className="flex gap-2">
              <input
                type="text" name="shipZipcode" value={data.shipZipcode || ''} onChange={handleChange}
                className="input-token flex-1" placeholder="우편번호"
              />
              <button
                type="button" onClick={onAddressSearch}
                className="px-4 py-2 bg-primary-500 text-bg-root rounded-token text-sm font-medium whitespace-nowrap hover:shadow-glow-sm transition-all"
              >
                주소 검색
              </button>
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-xs text-text-sub font-bold">주소</label>
            <input
              type="text" name="shipAddress1" value={data.shipAddress1 || ''} onChange={handleChange}
              className="input-token" placeholder="주소"
            />
          </div>
          <InputGroup label="상세주소" name="shipAddress2" value={data.shipAddress2 || ''} onChange={handleChange} />

          <div className="flex items-center gap-2">
            <input
              type="checkbox" id="isDefault"
              checked={data.isDefault || false}
              onChange={(e) => onChange('isDefault', e.target.checked)}
              className="accent-primary-500"
            />
            <label htmlFor="isDefault" className="text-sm text-text-main cursor-pointer">
              기본 배송지로 설정
            </label>
          </div>

          <button type="submit" className="mt-2 py-3 bg-primary-500 text-bg-root rounded-token font-bold text-base hover:shadow-glow transition-all">
            저장
          </button>
        </form>
      </div>
    </div>
  );
};

const InputGroup = ({ label, name, value, onChange }) => (
  <div className="flex flex-col gap-1.5">
    <label className="text-xs text-text-sub font-bold">{label}</label>
    <input
      type={name.includes('Name') || name.includes('Address') || name.includes('Phone') || name.includes('Zipcode') ? "text" : "number"}
      step="0.1"
      name={name}
      value={value !== null && value !== undefined ? value : ''}
      onChange={onChange}
      className="input-token"
    />
  </div>
);

export default ProfileList;
