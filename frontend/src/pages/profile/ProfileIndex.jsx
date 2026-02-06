import { Outlet } from "react-router-dom";
import BasicLayout from "../../components/layout/BasicLayout";

const ProfileIndex = () => {
  return (
    <BasicLayout>
      <Outlet />
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
