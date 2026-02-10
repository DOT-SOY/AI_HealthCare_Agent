import React, { useState, useEffect } from "react";
import { X, Check } from "lucide-react";
import dayjs from "dayjs";

/**
 * 인바디 분석 결과 검증 및 수정 모달
 */
const OcrInbodyVerifyModal = ({ isOpen, onClose, data, onSave }) => {
  const [formData, setFormData] = useState({});

  useEffect(() => {
    if (data) {
      setFormData({
        // 기본값 매핑
        height: data.height ?? "",
        weight: data.weight ?? "",
        skeletalMuscleMass: data.skeletalMuscleMass ?? "",
        bodyFatPercent: data.bodyFatPercent ?? "",
        bodyWater: data.bodyWater ?? "",
        protein: data.protein ?? "",
        minerals: data.minerals ?? "",
        bodyFatMass: data.bodyFatMass ?? "",
        
        // 조절 목표 등은 null일 수 있음
        targetWeight: data.targetWeight ?? "",
        weightControl: data.weightControl ?? "",
        fatControl: data.fatControl ?? "",
        muscleControl: data.muscleControl ?? "",
        
        measuredDate: dayjs().format("YYYY-MM-DD"), // 오늘 날짜 기본
      });
    }
  }, [data]);

  if (!isOpen) return null;

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSave = () => {
    // 숫자 변환 및 DTO 구성
    const payload = {
      ...data, // 기존 data(DTO) 구조 유지
      height: parseFloat(formData.height) || 0,
      weight: parseFloat(formData.weight) || 0,
      skeletalMuscleMass: parseFloat(formData.skeletalMuscleMass) || 0,
      bodyFatPercent: parseFloat(formData.bodyFatPercent) || 0,
      bodyWater: parseFloat(formData.bodyWater) || 0,
      protein: parseFloat(formData.protein) || 0,
      minerals: parseFloat(formData.minerals) || 0,
      bodyFatMass: parseFloat(formData.bodyFatMass) || 0,
      
      targetWeight: parseFloat(formData.targetWeight) || 0,
      weightControl: parseFloat(formData.weightControl) || 0,
      fatControl: parseFloat(formData.fatControl) || 0,
      muscleControl: parseFloat(formData.muscleControl) || 0,
      
      // 날짜 처리는 백엔드가 현재 시간(Instant.now)을 쓰거나, 
      // 필요하다면 measuredTime을 보낼 수 있음. (일단 백엔드 로직 따름)
    };
    onSave(payload);
  };

  // 공통 Input 스타일
  const inputStyle = {
    backgroundColor: "#222",
    border: "1px solid #444",
    color: "#fff",
    padding: "8px 12px",
    borderRadius: "6px",
    width: "100%",
    textAlign: "right",
    fontSize: "14px"
  };

  const labelStyle = {
    color: "#aaa",
    fontSize: "13px",
    marginBottom: "4px",
    display: "block"
  };

  const rowStyle = {
    display: "grid",
    gridTemplateColumns: "100px 1fr 40px",
    alignItems: "center",
    marginBottom: "10px",
    gap: "10px"
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        backgroundColor: "rgba(0,0,0,0.7)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 10001,
      }}
    >
      <div
        style={{
          width: 500,
          maxHeight: "90vh",
          backgroundColor: "#1a1a1a",
          borderRadius: "12px",
          boxShadow: "0 10px 40px rgba(0,0,0,0.5)",
          display: "flex",
          flexDirection: "column",
          color: "#fff",
          overflow: "hidden"
        }}
      >
        {/* Header */}
        <div style={{ padding: "16px 20px", borderBottom: "1px solid #333", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2 style={{ fontSize: "16px", fontWeight: "bold", margin: 0 }}>분석 결과</h2>
          <button onClick={onClose} style={{ background: "none", border: "none", color: "#666", cursor: "pointer" }}>
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div style={{ padding: "20px", overflowY: "auto", flex: 1 }}>
          <div style={{ marginBottom: "20px", fontSize: "13px", color: "#888" }}>
            추출된 체성분 수치입니다. 필요하면 수정한 뒤 저장하세요.
          </div>

          <div style={{ marginBottom: "20px" }}>
             <label style={labelStyle}>측정일</label>
             <input 
               type="date" 
               name="measuredDate"
               value={formData.measuredDate}
               onChange={handleChange}
               style={{ ...inputStyle, textAlign: "left" }} 
             />
          </div>

          {/* Fields */}
          <div style={rowStyle}><label style={{fontSize:13}}>체중</label><input type="number" name="weight" value={formData.weight} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>키</label><input type="number" name="height" value={formData.height} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>cm</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>골격근량</label><input type="number" name="skeletalMuscleMass" value={formData.skeletalMuscleMass} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>체지방률</label><input type="number" name="bodyFatPercent" value={formData.bodyFatPercent} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>%</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>체수분</label><input type="number" name="bodyWater" value={formData.bodyWater} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>L</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>단백질</label><input type="number" name="protein" value={formData.protein} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>무기질</label><input type="number" name="minerals" value={formData.minerals} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>체지방량</label><input type="number" name="bodyFatMass" value={formData.bodyFatMass} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
          
          <hr style={{ borderColor: "#333", margin: "20px 0" }} />
          
          <div style={rowStyle}><label style={{fontSize:13}}>적정체중</label><input type="number" name="targetWeight" value={formData.targetWeight} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>체중조절</label><input type="number" name="weightControl" value={formData.weightControl} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>지방조절</label><input type="number" name="fatControl" value={formData.fatControl} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13}}>근육조절</label><input type="number" name="muscleControl" value={formData.muscleControl} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color:'#666'}}>kg</span></div>
        </div>

        {/* Footer */}
        <div style={{ padding: "16px 20px", borderTop: "1px solid #333", display: "flex", gap: "10px" }}>
          <button 
            onClick={handleSave}
            style={{ 
              flex: 1, 
              backgroundColor: "#2ea043", 
              color: "#fff", 
              border: "none", 
              borderRadius: "6px", 
              padding: "12px", 
              fontWeight: "bold", 
              cursor: "pointer",
              fontSize: "14px"
            }}
          >
            저장
          </button>
          <button 
            onClick={onClose}
            style={{ 
              flex: 1, 
              backgroundColor: "#333", 
              color: "#ccc", 
              border: "none", 
              borderRadius: "6px", 
              padding: "12px", 
              fontWeight: "bold", 
              cursor: "pointer",
              fontSize: "14px"
            }}
          >
            아니요
          </button>
        </div>
      </div>
    </div>
  );
};

export default OcrInbodyVerifyModal;

