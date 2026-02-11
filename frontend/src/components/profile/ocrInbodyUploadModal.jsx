import React, { useMemo, useRef, useState } from "react";
import { X, ImagePlus } from "lucide-react";

/**
 * 인바디 OCR 업로드 모달
 * - 드래그&드랍
 * - 클릭 → 파일 선택
 */
const OcrInbodyUploadModal = ({ isOpen, onClose, onAnalyze }) => {
  const inputRef = useRef(null);
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [error, setError] = useState("");

  const previewUrl = useMemo(() => {
    if (!file) return null;
    return URL.createObjectURL(file);
  }, [file]);

  if (!isOpen) return null;

  const validateFile = (f) => {
    if (!f) return "파일이 없습니다.";
    const typeOk = ["image/png", "image/jpeg", "image/jpg", "image/webp"].includes(f.type);
    if (!typeOk) return "이미지 파일(png/jpg/webp)만 업로드할 수 있습니다.";
    const maxMb = 10;
    if (f.size > maxMb * 1024 * 1024) return `파일 용량이 너무 큽니다. (${maxMb}MB 이하)`;
    return "";
  };

  const setSelectedFile = (f) => {
    const msg = validateFile(f);
    if (msg) {
      setError(msg);
      setFile(null);
      return;
    }
    setError("");
    setFile(f);
  };

  const handlePick = () => {
    setError("");
    inputRef.current?.click?.();
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragOver(false);
    const f = e.dataTransfer?.files?.[0];
    if (f) setSelectedFile(f);
  };

  const handleAnalyze = async () => {
    if (!file || busy) return;
    setBusy(true);
    setError("");
    try {
      await onAnalyze?.(file);
      onClose?.();
    } catch (e) {
      const msg = e?.response?.data?.message || e?.message || "분석 중 오류가 발생했습니다.";
      setError(msg);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        backgroundColor: "rgba(0,0,0,0.55)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 10000,
      }}
      onMouseDown={(e) => {
        // backdrop 클릭 닫기
        if (e.target === e.currentTarget) onClose?.();
      }}
    >
      <div
        style={{
          width: 560,
          maxWidth: "92vw",
          background: "#1f1f1f",
          color: "#fff",
          borderRadius: 12,
          border: "1px solid rgba(255,255,255,0.08)",
          boxShadow: "0 10px 30px rgba(0,0,0,0.45)",
          position: "relative",
          padding: 22,
        }}
      >
        <button
          type="button"
          onClick={onClose}
          style={{
            position: "absolute",
            top: 12,
            right: 12,
            border: "none",
            background: "transparent",
            cursor: "pointer",
            color: "rgba(255,255,255,0.7)",
          }}
          aria-label="닫기"
        >
          <X size={18} />
        </button>

        <div style={{ fontSize: 18, fontWeight: 800, marginBottom: 8 }}>인바디 이미지 업로드</div>
        <div style={{ fontSize: 12, color: "rgba(255,255,255,0.65)", marginBottom: 14 }}>
          인바디 이미지 한 장을 넣어주세요. 이전 기록이 있으면 비교 분석합니다.
        </div>

        <div
          role="button"
          tabIndex={0}
          onClick={handlePick}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") handlePick();
          }}
          onDragEnter={(e) => {
            e.preventDefault();
            e.stopPropagation();
            setDragOver(true);
          }}
          onDragOver={(e) => {
            e.preventDefault();
            e.stopPropagation();
            setDragOver(true);
          }}
          onDragLeave={(e) => {
            e.preventDefault();
            e.stopPropagation();
            setDragOver(false);
          }}
          onDrop={handleDrop}
          style={{
            height: 150,
            borderRadius: 12,
            border: `2px dashed ${dragOver ? "rgba(204,255,0,0.9)" : "rgba(255,255,255,0.18)"}`,
            background: "rgba(255,255,255,0.04)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 10,
            cursor: "pointer",
            userSelect: "none",
          }}
        >
          {previewUrl ? (
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <img
                src={previewUrl}
                alt="업로드 미리보기"
                style={{
                  width: 90,
                  height: 90,
                  objectFit: "cover",
                  borderRadius: 10,
                  border: "1px solid rgba(255,255,255,0.12)",
                }}
              />
              <div>
                <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 4 }}>{file?.name}</div>
                <div style={{ fontSize: 12, color: "rgba(255,255,255,0.65)" }}>클릭해서 다른 이미지로 변경</div>
              </div>
            </div>
          ) : (
            <div style={{ textAlign: "center" }}>
              <div style={{ display: "flex", justifyContent: "center", marginBottom: 8, color: "rgba(255,255,255,0.6)" }}>
                <ImagePlus size={26} />
              </div>
              <div style={{ fontSize: 13, fontWeight: 700, color: "rgba(255,255,255,0.75)" }}>이미지 추가</div>
              <div style={{ fontSize: 11, marginTop: 6, color: "rgba(255,255,255,0.5)" }}>
                드래그&드랍 또는 클릭해서 업로드
              </div>
            </div>
          )}
        </div>

        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          style={{ display: "none" }}
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) setSelectedFile(f);
          }}
        />

        {error ? (
          <div style={{ marginTop: 10, fontSize: 12, color: "#ff7b7b" }}>{error}</div>
        ) : null}

        <button
          type="button"
          disabled={!file || busy}
          onClick={handleAnalyze}
          style={{
            width: "100%",
            marginTop: 14,
            height: 46,
            borderRadius: 10,
            border: "none",
            cursor: !file || busy ? "not-allowed" : "pointer",
            background: "#ccff00",
            color: "#111",
            fontWeight: 900,
            fontSize: 15,
            opacity: !file || busy ? 0.55 : 1,
          }}
        >
          {busy ? "분석 중..." : "분석 시작"}
        </button>
      </div>
    </div>
  );
};

export default OcrInbodyUploadModal;


