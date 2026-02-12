import React, { useState, useEffect } from "react";
import { X, Check } from "lucide-react";
import dayjs from "dayjs";

/**
 * 인바디 분석 결과 검증 및 수정 모달
 */
const OcrInbodyVerifyModal = ({ isOpen, onClose, data, onSave, isDark = true }) => {
  const [formData, setFormData] = useState({
    height: "",
    weight: "",
    skeletalMuscleMass: "",
    bodyFatPercent: "",
    bodyWater: "",
    protein: "",
    minerals: "",
    bodyFatMass: "",
    targetWeight: "",
    weightControl: "",
    fatControl: "",
    muscleControl: "",
    measuredDate: "",
  });

  useEffect(() => {
    if (data) {
      // measuredTime 파싱: 백엔드에서 Instant 타입으로 직렬화되어 ISO 8601 문자열로 옴
      let parsedDate = dayjs().format("YYYY-MM-DD"); // 기본값: 오늘 날짜
      
      if (data.measuredTime) {
        try {
          // 백엔드 DTO의 measuredTime은 Instant 타입이므로 JSON 직렬화 시 ISO 8601 형식
          // 예: "2025-01-30T14:28:00Z" 또는 "2025-01-30T14:28:00.000Z"
          const dateStr = String(data.measuredTime);
          
          // ISO 8601 형식 파싱
          if (dateStr.includes('T')) {
            parsedDate = dayjs(dateStr).format("YYYY-MM-DD");
          } else {
            // 다른 형식 시도 (혹시 모를 경우)
            const parsed = dayjs(dateStr);
            if (parsed.isValid()) {
              parsedDate = parsed.format("YYYY-MM-DD");
            }
          }
        } catch (e) {
          console.error("[OCR] measuredTime 파싱 에러:", data.measuredTime, e);
        }
      }

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
        
        measuredDate: parsedDate, // OCR 결과에서 파싱한 날짜 사용
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
    // measuredDate를 measuredTime으로 변환 (ISO 8601 형식)
    let measuredTime = null;
    if (formData.measuredDate) {
      try {
        // "YYYY-MM-DD" 형식을 ISO 8601 형식으로 변환 (백엔드 Instant 타입)
        const date = dayjs(formData.measuredDate);
        if (date.isValid()) {
          measuredTime = date.toISOString(); // "2025-02-24T00:00:00.000Z" 형식
        }
      } catch (e) {
        console.error("[OCR] measuredDate 변환 실패:", formData.measuredDate, e);
      }
    }
    
    // 불필요한 필드 제거 (id, createdAt, updatedAt, regDate, modDate, memberId 등)
    const { id, createdAt, updatedAt, regDate, modDate, memberId, ...cleanData } = data || {};
    
    const payload = {
      // 필요한 필드만 포함
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
      
      // measuredTime 추가 (백엔드 measured_time 컬럼에 저장됨)
      measuredTime: measuredTime,
      
      // exercisePurpose는 data에서 가져오되, 없으면 null
      exercisePurpose: cleanData.exercisePurpose || null,
    };
    
    console.log("[OCR] 저장 payload:", payload);
    onSave(payload);
  };

  // 테마별 스타일
  const overlayBg = isDark ? "rgba(0,0,0,0.7)" : "rgba(0,0,0,0.4)";
  const modalBg = isDark ? "#1a1a1a" : "#ffffff";
  const modalColor = isDark ? "#fff" : "#000";
  const borderColor = isDark ? "#333" : "#e0e0e0";
  const inputBg = isDark ? "#222" : "#f5f5f5";
  const inputBorder = isDark ? "#444" : "#ddd";
  const inputColor = isDark ? "#fff" : "#000";
  const labelColor = isDark ? "#aaa" : "#666";
  const textColor = isDark ? "#888" : "#666";
  const unitColor = isDark ? "#666" : "#999";
  const closeBtnColor = isDark ? "#666" : "#999";
  const cancelBtnBg = isDark ? "#333" : "#e0e0e0";
  const cancelBtnColor = isDark ? "#ccc" : "#666";

  // 공통 Input 스타일
  const inputStyle = {
    backgroundColor: inputBg,
    border: `1px solid ${inputBorder}`,
    color: inputColor,
    padding: "8px 12px",
    borderRadius: "6px",
    width: "100%",
    textAlign: "right",
    fontSize: "14px"
  };

  const labelStyle = {
    color: labelColor,
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
        backgroundColor: overlayBg,
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
          backgroundColor: modalBg,
          borderRadius: "12px",
          boxShadow: isDark ? "0 10px 40px rgba(0,0,0,0.5)" : "0 10px 40px rgba(0,0,0,0.15)",
          display: "flex",
          flexDirection: "column",
          color: modalColor,
          overflow: "hidden"
        }}
      >
        {/* Header */}
        <div style={{ padding: "16px 20px", borderBottom: `1px solid ${borderColor}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2 style={{ fontSize: "16px", fontWeight: "bold", margin: 0, color: modalColor }}>분석 결과</h2>
          <button onClick={onClose} style={{ background: "none", border: "none", color: closeBtnColor, cursor: "pointer" }}>
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div style={{ padding: "20px", overflowY: "auto", flex: 1 }}>
          <div style={{ marginBottom: "20px", fontSize: "13px", color: textColor }}>
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
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>체중</label><input type="number" name="weight" value={formData.weight} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>키</label><input type="number" name="height" value={formData.height} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>cm</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>골격근량</label><input type="number" name="skeletalMuscleMass" value={formData.skeletalMuscleMass} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>체지방률</label><input type="number" name="bodyFatPercent" value={formData.bodyFatPercent} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>%</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>체수분</label><input type="number" name="bodyWater" value={formData.bodyWater} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>L</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>단백질</label><input type="number" name="protein" value={formData.protein} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>무기질</label><input type="number" name="minerals" value={formData.minerals} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>체지방량</label><input type="number" name="bodyFatMass" value={formData.bodyFatMass} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
          
          <hr style={{ borderColor: borderColor, margin: "20px 0" }} />
          
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>적정체중</label><input type="number" name="targetWeight" value={formData.targetWeight} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>체중조절</label><input type="number" name="weightControl" value={formData.weightControl} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>지방조절</label><input type="number" name="fatControl" value={formData.fatControl} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
          <div style={rowStyle}><label style={{fontSize:13, color: labelColor}}>근육조절</label><input type="number" name="muscleControl" value={formData.muscleControl} onChange={handleChange} style={inputStyle} /><span style={{fontSize:12, color: unitColor}}>kg</span></div>
        </div>

        {/* Footer */}
        <div style={{ padding: "16px 20px", borderTop: `1px solid ${borderColor}`, display: "flex", gap: "10px" }}>
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
              backgroundColor: cancelBtnBg, 
              color: cancelBtnColor, 
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

