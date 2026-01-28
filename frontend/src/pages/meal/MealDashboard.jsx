import React, { useState, useEffect } from 'react';
import { mealApi } from '../../api/mealApi';

const buildDashboardData = (incoming) => {
    const base = {
        calories: { current: 0, goal: 0, percent: 0, status: "LACK" },
        carbs: { current: 0, goal: 0, percent: 0, status: "LACK" },
        protein: { current: 0, goal: 0, percent: 0, status: "LACK" },
        fat: { current: 0, goal: 0, percent: 0, status: "LACK" },
        breakfast: { totalCalories: 0, meals: [], percentCarbs: 0, percentProtein: 0, percentFat: 0 },
        lunch: { totalCalories: 0, meals: [], percentCarbs: 0, percentProtein: 0, percentFat: 0 },
        dinner: { totalCalories: 0, meals: [], percentCarbs: 0, percentProtein: 0, percentFat: 0 },
        analysisComments: []
    };

    if (!incoming) return base;

    return {
        ...base,
        ...incoming,
        calories: incoming.calories ?? base.calories,
        carbs: incoming.carbs ?? base.carbs,
        protein: incoming.protein ?? base.protein,
        fat: incoming.fat ?? base.fat,
        breakfast: incoming.breakfast ?? base.breakfast,
        lunch: incoming.lunch ?? base.lunch,
        dinner: incoming.dinner ?? base.dinner,
        analysisComments: Array.isArray(incoming.analysisComments) ? incoming.analysisComments : base.analysisComments,
    };
};

const formatDisplayDate = (dateString) => {
    if (!dateString) return "";
    const date = new Date(`${dateString}T00:00:00`);
    if (Number.isNaN(date.getTime())) return "";
    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "long",
        day: "numeric",
        weekday: "short",
    }).format(date);
};

// 연도 옵션 생성 (현재 연도 기준 ±2년)
const buildYearOptions = () => {
    const currentYear = new Date().getFullYear();
    const years = [];
    for (let i = -2; i <= 2; i += 1) {
        years.push(currentYear + i);
    }
    return years;
};

// 월 옵션 생성 (1-12)
const buildMonthOptions = () => {
    return Array.from({ length: 12 }, (_, i) => i + 1);
};

// 일 옵션 생성 (년/월에 따라 일수 계산)
const buildDayOptions = (year, month) => {
    if (!year || !month) return [];
    const daysInMonth = new Date(year, month, 0).getDate();
    return Array.from({ length: daysInMonth }, (_, i) => i + 1);
};

const MealDashboard = () => {
    const [data, setData] = useState(null);
    const [selectedDate, setSelectedDate] = useState(null);
    const [isDateOpen, setIsDateOpen] = useState(false);
    
    // 연도/월/일 개별 선택 상태
    const [selectedYear, setSelectedYear] = useState(null);
    const [selectedMonth, setSelectedMonth] = useState(null);
    const [selectedDay, setSelectedDay] = useState(null);

    const fetchData = async (dateOverride) => {
        try {
            const queryDate = dateOverride || selectedDate || new Date().toISOString().split('T')[0];
            // mealApi.getDashboard는 DTO를 그대로 반환함
            const response = await mealApi.getDashboard(queryDate);
            console.log("[MealDashboard] API 응답:", response);
            if (response) {
                setData(response);
                // dateOverride가 있으면 그대로 유지, 없으면 response.date 사용
                if (!dateOverride) {
                    setSelectedDate(response.date || queryDate);
                }
            } else {
                console.warn("[MealDashboard] 응답이 null입니다.");
                // 빈 데이터라도 기본 구조는 유지
                setData(null);
            }
        } catch (err) {
            console.error("대시보드 데이터 로딩 실패:", err);
            // 에러 발생 시에도 빈 데이터로 설정하여 UI가 사라지지 않도록
            setData(null);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    // 초기 날짜 파싱 (서버 시간 기준)
    useEffect(() => {
        const currentDate = selectedDate || data?.date || new Date().toISOString().split('T')[0];
        if (currentDate) {
            const [year, month, day] = currentDate.split('-').map(Number);
            if (!selectedYear) setSelectedYear(year);
            if (!selectedMonth) setSelectedMonth(month);
            if (!selectedDay) setSelectedDay(day);
        }
    }, [data, selectedDate]);

    const d = buildDashboardData(data);

    const displayDateText = formatDisplayDate(selectedDate || d.date);
    
    const yearOptions = buildYearOptions();
    const monthOptions = buildMonthOptions();
    const dayOptions = buildDayOptions(selectedYear, selectedMonth);

    // 연도/월/일 변경 시 날짜 조합 및 데이터 로드
    const handleDateChange = async (type, value) => {
        let newYear = selectedYear;
        let newMonth = selectedMonth;
        let newDay = selectedDay;

        if (type === 'year') {
            newYear = value;
            setSelectedYear(value);
            // 월/일 유효성 검사 (예: 2월 30일 -> 2월 28일로 조정)
            const maxDay = new Date(value, newMonth || 1, 0).getDate();
            if (newDay && newDay > maxDay) {
                newDay = maxDay;
                setSelectedDay(maxDay);
            }
        } else if (type === 'month') {
            newMonth = value;
            setSelectedMonth(value);
            // 일 유효성 검사
            const maxDay = new Date(newYear || new Date().getFullYear(), value, 0).getDate();
            if (newDay && newDay > maxDay) {
                newDay = maxDay;
                setSelectedDay(maxDay);
            }
        } else if (type === 'day') {
            newDay = value;
            setSelectedDay(value);
        }

        // 모든 값이 있으면 날짜 조합
        if (newYear && newMonth && newDay) {
            const dateString = `${newYear}-${String(newMonth).padStart(2, '0')}-${String(newDay).padStart(2, '0')}`;
            setSelectedDate(dateString);
            await fetchData(dateString);
        }
    };

    const handleSelectDate = async (dateValue) => {
        setIsDateOpen(false);
        // 날짜를 먼저 설정하고 fetchData 호출
        setSelectedDate(dateValue);
        await fetchData(dateValue);
    };

    return (
        <div className="w-full min-h-screen flex flex-col bg-[#121212] text-white font-sans">
            <div className="p-4">
            <style>{`
                @import url('https://fonts.googleapis.com/css2?family=Pretendard:wght@400;600;800&display=swap');
                body { font-family: 'Pretendard', sans-serif; background-color: #121212; color: white; }
                .neon-text-green { color: #CCFF00; text-shadow: 0 0 10px rgba(204, 255, 0, 0.6); }
                .neon-text-yellow { color: #FACC15; text-shadow: 0 0 10px rgba(250, 204, 21, 0.6); }
                .neon-text-blue { color: #60A5FA; text-shadow: 0 0 10px rgba(96, 165, 250, 0.6); }
                .neon-text-red { color: #EF4444; text-shadow: 0 0 10px rgba(239, 68, 68, 0.6); }
                .neon-text-gray { color: #9CA3AF; text-shadow: 0 0 10px rgba(156, 163, 175, 0.4); }
                .ring-neon-green { border-color: #CCFF00; box-shadow: 0 0 12px rgba(204, 255, 0, 0.4); }
                .ring-neon-yellow { border-color: #FACC15; box-shadow: 0 0 12px rgba(250, 204, 21, 0.4); }
                .ring-neon-blue { border-color: #3B82F6; box-shadow: 0 0 12px rgba(59, 130, 246, 0.4); }
                .ring-neon-red { border-color: #EF4444; box-shadow: 0 0 12px rgba(239, 68, 68, 0.4); }
                .ring-neon-gray { border-color: #6B7280; box-shadow: 0 0 12px rgba(107, 114, 128, 0.35); }
                .card-bg { background-color: #1E1E1E; }
                .bg-carb { background: linear-gradient(180deg, #D4C4A8 0%, #B8A88A 100%); }
                .bg-prot { background: linear-gradient(180deg, #F3F4F6 0%, #D1D5DB 100%); }
                .bg-fat  { background: linear-gradient(180deg, #FFB700 0%, #E6A600 100%); }
                ::-webkit-scrollbar { width: 6px; height: 6px; }
                ::-webkit-scrollbar-track { background: #121212; }
                ::-webkit-scrollbar-thumb { background: #333; border-radius: 3px; }
            `}</style>

            <header className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-4 mb-6">
                <h1 className="text-3xl font-extrabold italic"><span className="text-white">Today's</span> <span className="neon-text-green">Meal Plan</span></h1>
                <div className="relative self-start sm:self-auto flex items-center gap-2">
                    <button
                        type="button"
                        onClick={() => setIsDateOpen((prev) => !prev)}
                        className="text-gray-300 font-semibold px-3 py-1.5 border border-gray-700 rounded-lg bg-black/30 hover:bg-black/50 transition whitespace-nowrap"
                    >
                        {displayDateText || "날짜 선택"}
                    </button>
                    {isDateOpen && (
                        <div className="absolute left-0 top-full mt-2 bg-[#1E1E1E] border border-gray-700 rounded-lg shadow-lg z-20 p-4 flex gap-3 items-end">
                            {/* 연도 선택 */}
                            <div className="flex flex-col">
                                <label className="text-xs text-gray-400 mb-1">연도</label>
                                <select
                                    value={selectedYear || ''}
                                    onChange={(e) => handleDateChange('year', Number(e.target.value))}
                                    className="bg-black/50 border border-gray-700 rounded px-2 py-1.5 text-white text-sm min-w-[80px] focus:outline-none focus:border-[#CCFF00]"
                                >
                                    <option value="">연도</option>
                                    {yearOptions.map((year) => (
                                        <option key={year} value={year}>
                                            {year}년
                                        </option>
                                    ))}
                                </select>
                            </div>
                            
                            {/* 월 선택 */}
                            <div className="flex flex-col">
                                <label className="text-xs text-gray-400 mb-1">월</label>
                                <select
                                    value={selectedMonth || ''}
                                    onChange={(e) => handleDateChange('month', Number(e.target.value))}
                                    className="bg-black/50 border border-gray-700 rounded px-2 py-1.5 text-white text-sm min-w-[70px] focus:outline-none focus:border-[#CCFF00]"
                                >
                                    <option value="">월</option>
                                    {monthOptions.map((month) => (
                                        <option key={month} value={month}>
                                            {month}월
                                        </option>
                                    ))}
                                </select>
                            </div>
                            
                            {/* 일 선택 */}
                            <div className="flex flex-col">
                                <label className="text-xs text-gray-400 mb-1">일</label>
                                <select
                                    value={selectedDay || ''}
                                    onChange={(e) => handleDateChange('day', Number(e.target.value))}
                                    className="bg-black/50 border border-gray-700 rounded px-2 py-1.5 text-white text-sm min-w-[70px] focus:outline-none focus:border-[#CCFF00]"
                                    disabled={!selectedYear || !selectedMonth}
                                >
                                    <option value="">일</option>
                                    {dayOptions.map((day) => (
                                        <option key={day} value={day}>
                                            {day}일
                                        </option>
                                    ))}
                                </select>
                            </div>
                            
                            {/* 닫기 버튼 */}
                            <button
                                type="button"
                                onClick={() => setIsDateOpen(false)}
                                className="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-white text-sm rounded transition"
                            >
                                닫기
                            </button>
                        </div>
                    )}
                </div>
            </header>

            {/* 1. 상단: 원형 그래프 섹션 (v7 디자인) */}
            <section className="card-bg rounded-2xl p-4 mb-6 shadow-lg border border-gray-800">
                <h2 className="text-base font-bold mb-3 border-l-4 border-[#CCFF00] pl-2">일일 목표 달성률</h2>
                <div className="grid grid-cols-4 gap-2 text-center items-end">
                    <StatusCircle label="탄수화물" unit="g" data={d.carbs} />
                    <StatusCircle label="단백질" unit="g" data={d.protein} />
                    <StatusCircle label="지방" unit="g" data={d.fat} />
                    <StatusCircle label="칼로리" unit="kcal" data={d.calories} />
                </div>
            </section>

            {/* 2. 중단: 식단 카드 섹션 (v7 디자인) */}
            <section className="flex space-x-3 mb-6 overflow-x-auto pb-4">
                <MealCard title="아침" data={d.breakfast} />
                <MealCard title="점심" data={d.lunch} />
                <MealCard title="저녁" data={d.dinner} />
            </section>

            {/* 3. 하단: 분석 섹션 (v7 Before/After 디자인 적용) */}
            <section className="card-bg rounded-2xl border border-gray-700 overflow-hidden shadow-lg flex-grow mb-10">
                <div className="flex border-b border-gray-700 bg-black/40">
                    <button className="flex-1 py-4 text-sm font-bold border-b-2 border-[#CCFF00] text-[#CCFF00] bg-white/5 tracking-wider">식단 변동 내역</button>
                    <button className="flex-1 py-4 text-sm font-bold text-gray-500 hover:text-white transition tracking-wider">AI 식단 분석</button>
                </div>
                <div className="p-6 h-[450px] overflow-y-auto text-sm leading-7 text-gray-300 scrollbar-thin scrollbar-thumb-gray-600">
                    
                    {/* 데이터 렌더링: 단순 텍스트가 아니라 디자인 틀에 맞춰서 렌더링 */}
                    {d.analysisComments && d.analysisComments.length > 0 ? (
                        d.analysisComments.map((comment, idx) => {
                            // 단순 메시지인 경우 (추가/삭제 등) -> 심플 박스
                            if (!comment.includes("->")) {
                                return (
                                    <div key={idx} className="mb-4">
                                        <p className="mb-2"><span className="neon-text-blue font-bold bg-blue-400/10 px-2 py-1 rounded text-xs tracking-wide">[알림]</span> 식단 변동</p>
                                        <p className="text-gray-400 pl-3 border-l-2 border-blue-500/30">{comment}</p>
                                    </div>
                                );
                            }
                            // 변경 내역인 경우 (Before -> After) -> 디자인 박스 적용
                            // 예: "[변경] 점심 식단이 변경되었습니다: [현미밥] -> [라면]"
                            const parts = comment.split("->");
                            const beforeText = parts[0].split(":")[1]?.replace("[", "").replace("]", "").trim() || "이전 식단";
                            const afterText = parts[1]?.replace("[", "").replace("]", "").trim() || "변경 식단";

                            return (
                                <div key={idx} className="mb-6">
                                    <p className="mb-2"><span className="text-red-400 font-bold bg-red-400/10 px-2 py-1 rounded text-xs tracking-wide">[변경]</span> 식단이 변경되었습니다.</p>
                                    <div className="bg-black/40 border border-gray-700 p-4 rounded-lg mb-4">
                                        <div className="grid grid-cols-[50px_1fr] gap-2 mb-2 items-center">
                                            <span className="text-gray-500 text-xs font-bold uppercase">Before</span>
                                            <span className="line-through text-gray-500">{beforeText}</span> 
                                        </div>
                                        <div className="grid grid-cols-[50px_1fr] gap-2 items-center">
                                            <span className="text-[#CCFF00] text-xs font-bold uppercase">After</span>
                                            <span className="text-white font-bold">{afterText}</span>
                                        </div>
                                    </div>
                                    <p className="pl-3 border-l-2 border-red-500 text-gray-400">
                                        {/* 상세 영양소 차이는 백엔드에서 줄바꿈(\n)으로 온다고 가정 */}
                                        변경된 식단으로 인해 영양소 섭취량에 변화가 있습니다.
                                    </p>
                                </div>
                            );
                        })
                    ) : (
                        <p className="text-gray-500 text-center py-10">변동 내역이 없습니다.</p>
                    )}
                </div>
            </section>
            </div>
        </div>
    );
};

// [하위 컴포넌트들] - v7 디자인 100% 동일 유지
const StatusCircle = ({ label, unit, data }) => {
    const status = data?.status || "LACK";
    const styleMap = {
        GOOD: { ring: "ring-neon-green", text: "neon-text-green", badgeText: "text-[#CCFF00]" },
        SAFE: { ring: "ring-neon-yellow", text: "neon-text-yellow", badgeText: "text-[#FACC15]" },
        LOW: { ring: "ring-neon-blue", text: "neon-text-blue", badgeText: "text-[#60A5FA]" },
        LACK: { ring: "ring-neon-gray", text: "neon-text-gray", badgeText: "text-[#9CA3AF]" },
        FAIL: { ring: "ring-neon-red", text: "neon-text-red", badgeText: "text-[#EF4444]" }
    };
    const s = styleMap[status] || styleMap.LACK;
    return (
        <div className="flex flex-col items-center">
            <div className={`w-16 h-16 rounded-full border-4 flex flex-col items-center justify-center mb-1 bg-black/40 relative ${s.ring}`}>
                <span className={`text-[10px] font-extrabold leading-none mb-0.5 ${s.badgeText}`}>{status}</span>
                <span className="text-sm font-bold text-white">{data?.percent || 0}%</span>
            </div>
            <p className="text-lg font-semibold mt-1">
                <span className={`font-bold ${s.text}`}>{data?.current || 0}</span>
                <span className="text-sm text-gray-500">/{data?.goal || 0}{unit}</span>
            </p>
            <span className="text-sm font-bold text-gray-400 mt-1">{label}</span>
        </div>
    );
};

const MealCard = ({ title, data }) => {
    const safeData = data || { totalCalories: 0, meals: [], percentCarbs: 0, percentProtein: 0, percentFat: 0 };
    const safeMeals = Array.isArray(safeData.meals) ? safeData.meals : [];
    const badgeClass = (title === '아침' || title === '저녁') ? "bg-[#CCFF00] text-black" : "bg-gray-700 text-white";
    // 점심 경고 테두리 예시 로직 (데이터에 따라 동적 적용 가능)
    const warningClass = title === '점심' && safeData.totalCalories > 1000 ? "border-2 border-red-500 shadow-[0_0_15px_rgba(239,68,68,0.3)]" : "border border-gray-700";

    return (
        <div className={`card-bg rounded-2xl min-w-[32%] h-[400px] flex flex-col px-2 py-4 shadow-lg relative ${warningClass}`}>
            <div className="flex justify-between items-center mb-3 border-b border-gray-700 pb-2 px-2">
                <span className={`text-xs font-bold px-2 py-1 rounded-full ${badgeClass}`}>{title}</span>
                <span className="text-lg font-bold text-white">{safeData.totalCalories || 0}<span className="text-xs font-normal text-gray-400"> kcal</span></span>
            </div>
            <div className="flex-grow overflow-y-auto space-y-2 mb-3 px-2">
                {safeMeals.length > 0 ? (
                    safeMeals.map((m, i) => (
                        <p key={i} className="text-sm text-white">• {m.foodName || '식단 정보 없음'}</p>
                    ))
                ) : (
                    <p className="text-sm text-gray-500 text-center py-4">식단이 없습니다</p>
                )}
            </div>
            {/* 탄/단/지 바는 0%여도 모양(3칸)은 항상 유지 */}
            <div className="absolute bottom-4 left-2 right-2 h-8 rounded-lg overflow-hidden flex text-[10px] text-center font-bold leading-8 shadow-md">
                <div className="bg-carb w-1/3 text-black border-r border-black/10">탄 {safeData.percentCarbs || 0}%</div>
                <div className="bg-prot w-1/3 text-black border-r border-black/10">단 {safeData.percentProtein || 0}%</div>
                <div className="bg-fat w-1/3 text-black">지 {safeData.percentFat || 0}%</div>
            </div>
        </div>
    );
};

export default MealDashboard;