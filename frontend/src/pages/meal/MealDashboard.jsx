import React, { useState, useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { mealApi } from '../../api/mealApi';
import { useWebSocket } from '../../hooks/useWebSocket';
import ConfirmModal from '../../components/common/ConfirmModal';

const MealDashboard = () => {
    const [searchParams] = useSearchParams();
    const [data, setData] = useState(null);
    const [busy, setBusy] = useState(false);
    const replanFallbackTimerRef = useRef(null);
    const { connectWebSocket, subscribeToMealReplan, subscribeToMealChanged, disconnect } = useWebSocket();

    // 끼니 생략 Confirm(기본 confirm 대신 토큰 스타일 모달)
    const [skipConfirmOpen, setSkipConfirmOpen] = useState(false);
    const [replanConfirmOpen, setReplanConfirmOpen] = useState(false);
    const [pendingSkipMealTime, setPendingSkipMealTime] = useState(null);
    const [pendingSkipHasOtherPlanned, setPendingSkipHasOtherPlanned] = useState(false);

    const createDateFromString = (dateStr) => {
        const [y, m, d] = dateStr.split('-').map(Number);
        return new Date(y, m - 1, d);
    };

    const initialDateParam = searchParams.get('date');
    const [selectedDate, setSelectedDate] = useState(() => {
        if (initialDateParam) {
            return createDateFromString(initialDateParam);
        }
        return new Date();
    });
    const selectedDateRef = useRef(selectedDate);
    const [showYearDropdown, setShowYearDropdown] = useState(false);
    const [showMonthDropdown, setShowMonthDropdown] = useState(false);
    const [showDayDropdown, setShowDayDropdown] = useState(false);

    // 날짜 범위: 현재 년도 기준 전후 2년
    const currentYear = new Date().getFullYear();
    const years = Array.from({ length: 5 }, (_, i) => currentYear - 2 + i);
    const months = Array.from({ length: 12 }, (_, i) => i + 1);
    
    // 선택된 년/월에 맞는 일수 계산
    const getDaysInMonth = (year, month) => {
        return new Date(year, month, 0).getDate();
    };
    const days = Array.from({ length: getDaysInMonth(selectedDate.getFullYear(), selectedDate.getMonth() + 1) }, (_, i) => i + 1);

    const formatDate = (date) => {
        const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
        const year = date.getFullYear();
        const month = date.getMonth() + 1;
        const day = date.getDate();
        const weekday = weekdays[date.getDay()];
        return `${year}년 ${month}월 ${day}일 (${weekday})`;
    };

    const formatDateForApi = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    };

    const handleDateChange = (type, value) => {
        const newDate = new Date(selectedDate);
        if (type === 'year') {
            newDate.setFullYear(value);
        } else if (type === 'month') {
            newDate.setMonth(value - 1);
        } else if (type === 'day') {
            newDate.setDate(value);
        }
        // 일자가 유효하지 않으면 (예: 2월 30일 -> 2월 28일로 조정)
        const maxDay = getDaysInMonth(newDate.getFullYear(), newDate.getMonth() + 1);
        if (newDate.getDate() > maxDay) {
            newDate.setDate(maxDay);
        }
        setSelectedDate(newDate);
        setShowYearDropdown(false);
        setShowMonthDropdown(false);
        setShowDayDropdown(false);
    };

    const reload = async (targetDate = null) => {
        try {
            // targetDate가 있으면 그것을, 없으면 현재 selectedDate를 사용
            const dateToLoad = targetDate || selectedDate;
            const dateStr = formatDateForApi(dateToLoad);
            const response = await mealApi.getDashboard(dateStr);
            if (response) setData(response);
        } catch (err) {
            console.error("대시보드 데이터 로딩 실패:", err);
        }
    };

    useEffect(() => {
        selectedDateRef.current = selectedDate;
        reload();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedDate]);

    // 비동기 AI 생성/재정비가 끝났을 때 새로고침 없이 자동 반영
    useEffect(() => {
        connectWebSocket();

        const subReplan = subscribeToMealReplan(() => {
            // Replan 완료 시 대시보드 재조회
            if (replanFallbackTimerRef.current) {
                clearTimeout(replanFallbackTimerRef.current);
                replanFallbackTimerRef.current = null;
            }
            reload(selectedDateRef.current);
        });

        const subChanged = subscribeToMealChanged(() => {
            // 식단 변경(생성/토글/비전 등) 감지 시 대시보드 재조회
            reload(selectedDateRef.current);
        });

        return () => {
            try { subReplan?.unsubscribe?.(); } catch (_) {}
            try { subChanged?.unsubscribe?.(); } catch (_) {}

            if (replanFallbackTimerRef.current) {
                clearTimeout(replanFallbackTimerRef.current);
                replanFallbackTimerRef.current = null;
            }
            disconnect();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const d = data || {
        calories: { current: 0, goal: 0, percent: 0, status: "-" },
        carbs: { current: 0, goal: 0, percent: 0, status: "-" },
        protein: { current: 0, goal: 0, percent: 0, status: "-" },
        fat: { current: 0, goal: 0, percent: 0, status: "-" },
        breakfast: { totalCalories: 0, meals: [], percentCarbs: 0, percentProtein: 0, percentFat: 0 },
        lunch: { totalCalories: 0, meals: [], percentCarbs: 0, percentProtein: 0, percentFat: 0 },
        dinner: { totalCalories: 0, meals: [], percentCarbs: 0, percentProtein: 0, percentFat: 0 },
        analysisComments: []
    };

    const allMeals = [
        ...(d.breakfast?.meals || []),
        ...(d.lunch?.meals || []),
        ...(d.dinner?.meals || []),
        ...(d.snack?.meals || []),
    ];

    const _mealsByTime = (mealTime) => {
        if (mealTime === 'BREAKFAST') return d.breakfast?.meals || [];
        if (mealTime === 'LUNCH') return d.lunch?.meals || [];
        if (mealTime === 'DINNER') return d.dinner?.meals || [];
        return [];
    };

    const handleMealBoxComplete = async (mealTime) => {
        if (busy) return;
        const mealsAll = _mealsByTime(mealTime) || [];
        const planned = mealsAll.filter((m) => m?.status === 'PLANNED');
        const eatenPlanned = mealsAll.filter((m) => m?.status === 'EATEN' && !m?.isAdditional);

        // 토글: PLANNED가 있으면 완료 처리, 아니면(이미 완료 상태) 취소(PLANNED 복구)
        const toUpdate = planned.length > 0 ? planned : eatenPlanned;
        if (toUpdate.length === 0) return;

        setBusy(true);
        try {
            const next = planned.length > 0 ? 'EATEN' : 'PLANNED';
            await Promise.all(toUpdate.map((m) => mealApi.toggleStatus(m.scheduleId, next)));
            await reload(selectedDate);
        } catch (e) {
            console.error('끼니 완료 처리 실패:', e);
            window.alert('처리 중 오류가 발생했습니다.');
        } finally {
            setBusy(false);
        }
    };

    const handleMealBoxSkip = async (mealTime) => {
        if (busy) return;
        const mealsAll = _mealsByTime(mealTime) || [];
        const planned = mealsAll.filter((m) => m?.status === 'PLANNED');

        const section =
            mealTime === 'BREAKFAST' ? d.breakfast :
            mealTime === 'LUNCH' ? d.lunch :
            mealTime === 'DINNER' ? d.dinner :
            null;
        const isMealTimeSkipped = !!section?.skipped;

        // 토글: PLANNED가 있으면 생략 처리(+재정비 질문), 아니면 취소(PLANNED 복구)
        if (planned.length === 0 && !isMealTimeSkipped) return;
        const isCancelling = planned.length === 0 && isMealTimeSkipped;
        if (isCancelling) {
            setBusy(true);
            try {
                const dateStr = formatDateForApi(selectedDate);
                await mealApi.toggleMealTimeSkip(dateStr, mealTime, false);
                await reload(selectedDate);
            } catch (e) {
                console.error('끼니 생략 취소 실패:', e);
                window.alert('처리 중 오류가 발생했습니다.');
            } finally {
                setBusy(false);
            }
            return;
        }

        // UX: 끼니 전체 생략 시, "아직 안 먹은 끼니"에 재구성(재정비)할지 물어봄
        const otherPlanned = allMeals.filter((m) => m?.status === 'PLANNED' && m?.mealTime !== mealTime);
        setPendingSkipMealTime(mealTime);
        setPendingSkipHasOtherPlanned(otherPlanned.length > 0);
        setSkipConfirmOpen(true);
    };

    const performMealTimeSkip = async (mealTime, shouldReplan) => {
        if (!mealTime) return;
        if (busy) return;
        setBusy(true);
        try {
            const dateStr = formatDateForApi(selectedDate);
            await mealApi.toggleMealTimeSkip(dateStr, mealTime, shouldReplan);
            if (shouldReplan) {
                // 서버가 비동기로 처리/WS가 없는 경우도 있어 fallback reload
                if (replanFallbackTimerRef.current) {
                    clearTimeout(replanFallbackTimerRef.current);
                }
                replanFallbackTimerRef.current = setTimeout(() => {
                    reload(selectedDate);
                    replanFallbackTimerRef.current = null;
                }, 2500);
            } else {
                await reload(selectedDate);
            }
        } catch (e) {
            console.error('끼니 생략 처리 실패:', e);
            window.alert('처리 중 오류가 발생했습니다.');
        } finally {
            setBusy(false);
        }
    };

    const handleItemComplete = async (meal) => {
        if (!meal?.scheduleId) return;
        if (busy) return;
        setBusy(true);
        try {
            const next = meal.status === 'EATEN' ? 'PLANNED' : 'EATEN';
            await mealApi.toggleStatus(meal.scheduleId, next);
            await reload(selectedDate);
        } catch (e) {
            console.error('항목 완료 처리 실패:', e);
            window.alert('처리 중 오류가 발생했습니다.');
        } finally {
            setBusy(false);
        }
    };

    const handleItemSkip = async (meal) => {
        if (!meal?.scheduleId) return;
        if (busy) return;
        setBusy(true);
        try {
            const next = meal.status === 'SKIPPED' ? 'PLANNED' : 'SKIPPED';
            await mealApi.toggleStatus(meal.scheduleId, next);
            await reload();
        } catch (e) {
            console.error('항목 생략 처리 실패:', e);
            window.alert('처리 중 오류가 발생했습니다.');
        } finally {
            setBusy(false);
        }
    };

  return (
        <div className="w-full">
            <header className="section-header-token flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-6">
                <div>
                    <h1 className="section-title"><span className="text-text-main">Today's </span><span className="text-primary-500">Meal Plan</span></h1>
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                    <div className="segment-btn relative cursor-pointer"
                         onClick={() => { setShowYearDropdown(!showYearDropdown); setShowMonthDropdown(false); setShowDayDropdown(false); }}>
                        {selectedDate.getFullYear()}년
                        {showYearDropdown && (
                            <div className="absolute top-full left-0 mt-2 bg-bg-card border border-border-default rounded-token shadow-lg z-50 max-h-48 overflow-y-auto min-w-[100px]">
                                {years.map(year => (
                                    <div key={year}
                                         className="px-4 py-2 hover:bg-gray-100 cursor-pointer text-text-main text-sm"
                                         onClick={(e) => { e.stopPropagation(); handleDateChange('year', year); }}>
                                        {year}년
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                    <div className="segment-btn relative cursor-pointer"
                         onClick={() => { setShowMonthDropdown(!showMonthDropdown); setShowYearDropdown(false); setShowDayDropdown(false); }}>
                        {selectedDate.getMonth() + 1}월
                        {showMonthDropdown && (
                            <div className="absolute top-full left-0 mt-2 bg-bg-card border border-border-default rounded-token shadow-lg z-50 max-h-48 overflow-y-auto min-w-[80px]">
                                {months.map(month => (
                                    <div key={month}
                                         className="px-4 py-2 hover:bg-gray-100 cursor-pointer text-text-main text-sm"
                                         onClick={(e) => { e.stopPropagation(); handleDateChange('month', month); }}>
                                        {month}월
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                    <div className="segment-btn relative cursor-pointer"
                         onClick={() => { setShowDayDropdown(!showDayDropdown); setShowYearDropdown(false); setShowMonthDropdown(false); }}>
                        {selectedDate.getDate()}일
                        {showDayDropdown && (
                            <div className="absolute top-full left-0 mt-2 bg-bg-card border border-border-default rounded-token shadow-lg z-50 max-h-48 overflow-y-auto min-w-[80px]">
                                {days.map(day => (
                                    <div key={day}
                                         className="px-4 py-2 hover:bg-gray-100 cursor-pointer text-text-main text-sm"
                                         onClick={(e) => { e.stopPropagation(); handleDateChange('day', day); }}>
                                        {day}일
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </header>

            {/* 1. 상단: 원형 그래프 섹션 */}
            <section className="card-token rounded-token p-4 mb-6 border border-border-default">
                <h2 className="text-base font-bold mb-3 border-l-4 border-primary-500 pl-2 text-text-main">일일 목표 달성률</h2>
                <div className="grid grid-cols-4 gap-2 text-center items-end">
                    <StatusCircle label="탄수화물" unit="g" data={d.carbs} type="green" />
                    <StatusCircle label="단백질" unit="g" data={d.protein} type="yellow" />
                    <StatusCircle label="지방" unit="g" data={d.fat} type="blue" />
                    <StatusCircle label="칼로리" unit="kcal" data={d.calories} type="red" />
                </div>
            </section>

            {/* 2. 중단: 식단 카드 섹션 */}
            <section className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                <MealCard title="아침" data={d.breakfast} mealTime="BREAKFAST" busy={busy} onMealComplete={handleMealBoxComplete} onMealSkip={handleMealBoxSkip} onItemComplete={handleItemComplete} onItemSkip={handleItemSkip} />
                <MealCard title="점심" data={d.lunch} mealTime="LUNCH" busy={busy} onMealComplete={handleMealBoxComplete} onMealSkip={handleMealBoxSkip} onItemComplete={handleItemComplete} onItemSkip={handleItemSkip} />
                <MealCard title="저녁" data={d.dinner} mealTime="DINNER" busy={busy} onMealComplete={handleMealBoxComplete} onMealSkip={handleMealBoxSkip} onItemComplete={handleItemComplete} onItemSkip={handleItemSkip} />
            </section>

            {/* 3. 하단: 분석 섹션 */}
            <section className="card-token rounded-token border border-border-default overflow-hidden flex-grow mb-10">
                <div className="flex border-b border-border-default bg-bg-surface">
                    <button type="button" className="flex-1 py-4 text-sm font-bold border-b-2 border-primary-500 text-primary-500 bg-bg-card/50 tracking-wider">식단 변동 내역</button>
                    <button type="button" className="flex-1 py-4 text-sm font-bold text-text-muted hover:text-text-main transition tracking-wider">AI 식단 분석</button>
                </div>
                <div className="p-6 h-[450px] overflow-y-auto text-sm leading-7 text-text-sub">
                    
                    {/* 데이터 렌더링: 단순 텍스트가 아니라 디자인 틀에 맞춰서 렌더링 */}
                    {d.analysisComments && d.analysisComments.length > 0 ? (
                        d.analysisComments.map((comment, idx) => {
                            if (!comment.includes("->")) {
                                return (
                                    <div key={idx} className="mb-4">
                                        <p className="mb-2"><span className="text-primary-500 font-bold bg-primary-500/10 px-2 py-1 rounded-token-sm text-xs tracking-wide">[알림]</span> 식단 변동</p>
                                        <p className="text-text-sub pl-3 border-l-2 border-primary-500/30">{comment}</p>
                                    </div>
                                );
                            }
                            const parts = comment.split("->");
                            const beforeText = parts[0].split(":")[1]?.replace("[", "").replace("]", "").trim() || "이전 식단";
                            const afterText = parts[1]?.replace("[", "").replace("]", "").trim() || "변경 식단";

                            return (
                                <div key={idx} className="mb-6">
                                    <p className="mb-2"><span className="text-accent-secondary font-bold bg-accent-secondary/10 px-2 py-1 rounded-token-sm text-xs tracking-wide">[변경]</span> 식단이 변경되었습니다.</p>
                                    <div className="bg-bg-surface border border-border-default p-4 rounded-token mb-4">
                                        <div className="grid grid-cols-[50px_1fr] gap-2 mb-2 items-center">
                                            <span className="text-text-muted text-xs font-bold uppercase">Before</span>
                                            <span className="line-through text-text-muted">{beforeText}</span>
                                        </div>
                                        <div className="grid grid-cols-[50px_1fr] gap-2 items-center">
                                            <span className="text-primary-500 text-xs font-bold uppercase">After</span>
                                            <span className="text-text-main font-bold">{afterText}</span>
                                        </div>
                                    </div>
                                    <p className="pl-3 border-l-2 border-accent-secondary text-text-sub">
                                        변경된 식단으로 인해 영양소 섭취량에 변화가 있습니다.
                                    </p>
                                </div>
                            );
                        })
                    ) : (
                        <p className="text-text-muted text-center py-10">변동 내역이 없습니다.</p>
                    )}
                </div>
            </section>

            {/* 끼니 생략 Confirm 모달 */}
            <ConfirmModal
                isOpen={skipConfirmOpen}
                title="끼니 생략"
                message="이 끼니를 생략할까요?"
                confirmText="생략"
                cancelText="취소"
                closeOnBackdrop={false}
                showCloseButton={false}
                onCancel={() => {
                    setSkipConfirmOpen(false);
                    setPendingSkipMealTime(null);
                    setPendingSkipHasOtherPlanned(false);
                }}
                onConfirm={() => {
                    setSkipConfirmOpen(false);
                    if (pendingSkipHasOtherPlanned) {
                        setReplanConfirmOpen(true);
                        return;
                    }
                    performMealTimeSkip(pendingSkipMealTime, false);
                    setPendingSkipMealTime(null);
                    setPendingSkipHasOtherPlanned(false);
                }}
            />

            {/* 재정비 Confirm 모달 (생략 후 추가 배분 여부) */}
            <ConfirmModal
                isOpen={replanConfirmOpen}
                title="재정비"
                message="아직 안 먹은 끼니에 재구성(재정비)해드릴까요?"
                confirmText="재정비"
                cancelText="생략만"
                closeOnBackdrop={false}
                showCloseButton={false}
                onCancel={() => {
                    setReplanConfirmOpen(false);
                    performMealTimeSkip(pendingSkipMealTime, false);
                    setPendingSkipMealTime(null);
                    setPendingSkipHasOtherPlanned(false);
                }}
                onConfirm={() => {
                    setReplanConfirmOpen(false);
                    performMealTimeSkip(pendingSkipMealTime, true);
                    setPendingSkipMealTime(null);
                    setPendingSkipHasOtherPlanned(false);
                }}
            />
        </div>
    );
};

/* 하위 컴포넌트 — 루틴/기록과 동일 토큰 사용 */
const StatusCircle = ({ label, unit, data, type }) => {
    const styleMap = {
        green: { ring: "border-primary-500 shadow-glow-sm", badgeText: "text-primary-500", valueText: "text-primary-500" },
        yellow: { ring: "border-amber-400", badgeText: "text-amber-400", valueText: "text-amber-400" },
        blue: { ring: "border-blue-400", badgeText: "text-blue-400", valueText: "text-blue-400" },
        red: { ring: "border-accent-secondary", badgeText: "text-accent-secondary", valueText: "text-accent-secondary" }
    };
    const s = styleMap[type] || styleMap.green;
    return (
        <div className="flex flex-col items-center">
            <div className={`w-16 h-16 rounded-full border-4 flex flex-col items-center justify-center mb-1 bg-bg-surface ${s.ring}`}>
                <span className={`text-[10px] font-extrabold leading-none mb-0.5 ${s.badgeText}`}>{data?.status || '-'}</span>
                <span className="text-sm font-bold text-text-main">{data?.percent || 0}%</span>
            </div>
            <p className="text-lg font-semibold mt-1 text-text-main">
                <span className={`font-bold ${s.valueText}`}>{data?.current || 0}</span>
                <span className="text-sm text-text-muted">/{data?.goal || 0}{unit}</span>
            </p>
            <span className="text-sm font-bold text-text-muted mt-1">{label}</span>
        </div>
    );
};

const MealCard = ({ title, data, mealTime, busy, onMealComplete, onMealSkip, onItemComplete, onItemSkip }) => {
    if (!data) return null;
    const badgeClass = (title === '아침' || title === '저녁') ? "bg-primary-500 text-bg-root" : "bg-gray-100 text-text-main";
    // 점심만 빨간 경고 테두리 노출되는 UX 제거 (모든 끼니 동일 디자인 유지)
    const warningClass = "border border-border-default";
    const mealTimeSkipped = !!data.skipped;
    const hasPlanned = (data.meals || []).some((m) => m?.status === 'PLANNED');
    const hasSkipped = (data.meals || []).some((m) => m?.status === 'SKIPPED' && !m?.isAdditional);
    const completeLabel = hasPlanned ? '완료' : '완료취소';
    const skipLabel = hasPlanned ? '생략' : (hasSkipped ? '생략취소' : '생략');
    const completeDisabled = !!busy || hasSkipped;
    const skipDisabled = !!busy || (!hasPlanned && !hasSkipped);

    return (
        <div className={`card-token rounded-token w-full h-[400px] flex flex-col px-3 py-4 relative ${warningClass}`}>
            <div className="flex justify-between items-center mb-3 border-b border-border-default pb-2 px-1">
                <div className="flex items-center gap-2">
                    <span className={`text-xs font-bold px-2 py-1 rounded-token-sm ${badgeClass}`}>{title}</span>
                    <button
                        type="button"
                        className={`px-2 py-1 rounded-token-sm text-xs font-bold bg-primary-500 text-bg-root ${completeDisabled ? 'opacity-50 cursor-not-allowed' : ''}`}
                        disabled={completeDisabled}
                        onClick={() => onMealComplete && onMealComplete(mealTime)}
                    >
                        {completeLabel}
                    </button>
                    <button
                        type="button"
                        className={`px-2 py-1 rounded-token-sm text-xs font-bold bg-accent-secondary text-white ${skipDisabled ? 'opacity-50 cursor-not-allowed' : ''}`}
                        disabled={skipDisabled}
                        onClick={() => onMealSkip && onMealSkip(mealTime)}
                    >
                        {skipLabel}
                    </button>
                </div>
                <span className="text-lg font-bold text-text-main">{data.totalCalories || 0}<span className="text-xs font-normal text-text-muted"> kcal</span></span>
            </div>
            <div className="flex-grow overflow-y-auto space-y-2 mb-3 px-1">
                {data.meals && data.meals.map((m, i) => {
                    const isEaten = m?.status === 'EATEN';
                    const isSkipped = m?.status === 'SKIPPED';
                    const itemCompleteLabel = isEaten ? '완료취소' : '완료';
                    const itemSkipLabel = isSkipped ? '생략취소' : '생략';
                    const itemCompleteDisabled = !!busy || isSkipped || mealTimeSkipped;
                    const itemSkipDisabled = !!busy || isEaten || mealTimeSkipped;
                    return (
                        <div key={m.scheduleId || i} className="flex items-center justify-between gap-2">
                            <div className={`text-sm flex-1 ${mealTimeSkipped || isSkipped ? 'text-text-muted opacity-70' : 'text-text-main'}`}>
                                <div className="flex items-center gap-2">
                                    <span>• {m.foodName}</span>
                                    {m?.isAdditional && !mealTimeSkipped && (
                                        <span className="text-[10px] font-bold text-primary-500 bg-primary-500/10 px-1.5 py-0.5 rounded-token-sm">
                                            추가
                                        </span>
                                    )}
                                    {isEaten && <span className="text-xs text-primary-500">(완료)</span>}
                                    {isSkipped && <span className="text-xs text-text-muted">(생략)</span>}
                                </div>
                            </div>
                            <div className="flex gap-1">
                                <button
                                    type="button"
                                    className={`px-2 py-1 rounded-token-sm text-xs font-bold bg-primary-500 text-bg-root ${itemCompleteDisabled ? 'opacity-50 cursor-not-allowed' : ''}`}
                                    disabled={itemCompleteDisabled}
                                    onClick={() => onItemComplete && onItemComplete(m)}
                                >
                                    {itemCompleteLabel}
                                </button>
                                <button
                                    type="button"
                                    className={`px-2 py-1 rounded-token-sm text-xs font-bold bg-accent-secondary text-white ${itemSkipDisabled ? 'opacity-50 cursor-not-allowed' : ''}`}
                                    disabled={itemSkipDisabled}
                                    onClick={() => onItemSkip && onItemSkip(m)}
                                >
                                    {itemSkipLabel}
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>
            <div className="absolute bottom-4 left-2 right-2 h-8 rounded-token overflow-hidden flex text-[10px] text-center font-bold leading-8">
                <div className="bg-amber-700/90 w-1/3 text-bg-root border-r border-black/10">탄 {data.percentCarbs || 0}%</div>
                <div className="bg-gray-400 w-1/3 text-bg-root border-r border-black/10">단 {data.percentProtein || 0}%</div>
                <div className="bg-amber-500 w-1/3 text-bg-root">지 {data.percentFat || 0}%</div>
            </div>
        </div>
    );
};

export default MealDashboard;