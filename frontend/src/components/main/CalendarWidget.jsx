import { useState, useEffect } from 'react';
import { mealApi } from '../../api/mealApi';
import { routineApi } from '../../api/routineApi';
import MealPreviewModal from './MealPreviewModal';
import RoutinePreviewModal from './RoutinePreviewModal';

export default function CalendarWidget() {
  const [selectedTag, setSelectedTag] = useState('MEAL'); // 'MEAL' or 'ROUTINE'
  const [currentDate, setCurrentDate] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState(null);
  const [mealData, setMealData] = useState(null);
  const [routineData, setRoutineData] = useState(null);
  const [isMealModalOpen, setIsMealModalOpen] = useState(false);
  const [isRoutineModalOpen, setIsRoutineModalOpen] = useState(false);
  const [mealDates, setMealDates] = useState([]); // 식사 데이터가 있는 날짜 배열 (YYYY-MM-DD)
  const [routineDates, setRoutineDates] = useState([]); // 루틴이 있는 날짜 배열 (YYYY-MM-DD)

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();

  const formatDateKey = (date) => {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  };

  // 달력 데이터 생성
  const getCalendarData = () => {
    const firstDay = new Date(year, month, 1);
    const startDate = new Date(firstDay);
    startDate.setDate(startDate.getDate() - firstDay.getDay()); // 일요일로 시작

    const days = [];
    const current = new Date(startDate);

    // 6주치 달력 (42일)
    for (let i = 0; i < 42; i++) {
      days.push(new Date(current));
      current.setDate(current.getDate() + 1);
    }

    return days;
  };

  const handleDateClick = async (date) => {
    setSelectedDate(date);
    const dateStr = formatDateKey(date); // 로컬 기준 YYYY-MM-DD

    if (selectedTag === 'MEAL') {
      try {
        const data = await mealApi.getDashboard(dateStr);
        setMealData(data);
        setIsMealModalOpen(true);
      } catch (error) {
        console.error('식사 데이터 조회 실패:', error);
        setMealData(null);
        setIsMealModalOpen(true);
      }
    } else {
      try {
        const data = await routineApi.getByDate(dateStr);
        setRoutineData(data);
        setIsRoutineModalOpen(true);
      } catch (error) {
        console.error('루틴 데이터 조회 실패:', error);
        setRoutineData(null);
        setIsRoutineModalOpen(true);
      }
    }
  };

  const handlePrevMonth = () => {
    setCurrentDate(new Date(year, month - 1, 1));
  };

  const handleNextMonth = () => {
    setCurrentDate(new Date(year, month + 1, 1));
  };

  const handlePrevYear = () => {
    setCurrentDate(new Date(year - 1, month, 1));
  };

  const handleNextYear = () => {
    setCurrentDate(new Date(year + 1, month, 1));
  };

  const calendarDays = getCalendarData();
  const weekDays = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  const isCurrentMonth = (date) => {
    return date.getMonth() === month;
  };

  const isToday = (date) => {
    const today = new Date();
    return (
      date.getDate() === today.getDate() &&
      date.getMonth() === today.getMonth() &&
      date.getFullYear() === today.getFullYear()
    );
  };

  // 현재 태그/월 기준으로 캘린더 마커 데이터 로딩
  useEffect(() => {
    const fetchMarkerData = async () => {
      if (selectedTag === 'MEAL') {
        try {
          const calendarData = await mealApi.getMonthlyCalendar(year, month);
          let dates = [];
          
          if (Array.isArray(calendarData) && calendarData.length > 0) {
            calendarData.forEach((item) => {
              const mealDate = item?.mealDate || item?.date || item?.meal_date;
              
              if (mealDate) {
                let dateStr = '';
                if (typeof mealDate === 'string') {
                  dateStr = mealDate.split('T')[0].trim();
                } else if (mealDate instanceof Date) {
                  dateStr = formatDateKey(mealDate);
                } else if (typeof mealDate === 'number') {
                  dateStr = formatDateKey(new Date(mealDate));
                }
                
                if (dateStr && /^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
                  dates.push(dateStr);
                }
              }
            });
          }
          
          if (dates.length === 0) {
            const daysInMonth = new Date(year, month + 1, 0).getDate();
            const checkPromises = [];
            
            for (let day = 1; day <= Math.min(daysInMonth, 10); day++) {
              const checkDate = new Date(year, month, day);
              const dateStr = formatDateKey(checkDate);
              
              checkPromises.push(
                mealApi.getDashboard(dateStr)
                  .then(data => {
                    if (!data) return null;
                    
                    const checkMeals = (section) => {
                      return section && 
                        section.meals && 
                        Array.isArray(section.meals) && 
                        section.meals.length > 0;
                    };
                    
                    const hasBreakfast = checkMeals(data.breakfast);
                    const hasLunch = checkMeals(data.lunch);
                    const hasDinner = checkMeals(data.dinner);
                    const hasSnack = checkMeals(data.snack);
                    
                    if (hasBreakfast || hasLunch || hasDinner || hasSnack) {
                      return dateStr;
                    }
                    return null;
                  })
                  .catch(() => null)
              );
            }
            
            const results = await Promise.all(checkPromises);
            dates = results.filter(d => d !== null);
          }
          
          setMealDates(dates);
        } catch (error) {
          console.error('❌ 식사 캘린더 조회 실패:', error);
          setMealDates([]);
        }
      } else {
        try {
          const routines = await routineApi.getHistory();
          const dates = Array.isArray(routines)
            ? routines
                .filter((r) => r.date)
                .map((r) => {
                  if (typeof r.date === 'string') {
                    return r.date.split('T')[0].trim();
                  }
                  return formatDateKey(new Date(r.date));
                })
            : [];
          setRoutineDates(dates);
        } catch (error) {
          console.error('루틴 히스토리 조회 실패:', error);
          setRoutineDates([]);
        }
      }
    };

    fetchMarkerData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedTag, year, month]);

  return (
    <div className="w-full">
      {/* Tag Selection + Calendar Header */}
      <div className="flex items-center justify-between mb-4">
        {/* 왼쪽: MEAL / ROUTINE 버튼 */}
        <div className="flex gap-2">
          <button
            onClick={() => setSelectedTag('MEAL')}
            className={`px-4 py-1 rounded-token text-sm font-medium transition-colors ${
              selectedTag === 'MEAL'
                ? 'bg-primary-500 text-bg-root'
                : 'bg-gray-100 text-text-main hover:bg-gray-200'
            }`}
          >
            MEAL
          </button>
          <button
            onClick={() => setSelectedTag('ROUTINE')}
            className={`px-4 py-1 rounded-token text-sm font-medium transition-colors ${
              selectedTag === 'ROUTINE'
                ? 'bg-primary-500 text-bg-root'
                : 'bg-gray-100 text-text-main hover:bg-gray-200'
            }`}
          >
            ROUTINE
          </button>
        </div>

        {/* 오른쪽: 날짜 네비게이션 */}
        <div className="flex items-center gap-1">
          <button
            onClick={handlePrevYear}
            className="p-1 text-text-sub hover:text-text-main transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
            </svg>
          </button>
          <button
            onClick={handlePrevMonth}
            className="p-1 text-text-sub hover:text-text-main transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <span className="text-text-main font-medium text-sm mx-1">{months[month]}</span>
          <span className="text-text-sub text-sm">{year}</span>
          <button
            onClick={handleNextMonth}
            className="p-1 text-text-sub hover:text-text-main transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </button>
          <button
            onClick={handleNextYear}
            className="p-1 text-text-sub hover:text-text-main transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 5l7 7-7 7M5 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>

      {/* Calendar Grid */}
      <div className="grid grid-cols-7 gap-1">
        {/* Weekday Headers */}
        {weekDays.map((day) => (
          <div key={day} className="text-center text-xs text-text-muted font-medium py-2">
            {day}
          </div>
        ))}

        {/* Calendar Days */}
        {calendarDays.map((date, index) => {
          const isCurrent = isCurrentMonth(date);
          const isTodayDate = isToday(date);
          const dateKey = formatDateKey(date);

          // 날짜 비교: 간단하고 확실한 방법
          let hasMealData = false;
          let hasRoutineData = false;
          
          if (selectedTag === 'MEAL') {
            // mealDates 배열에 정확히 일치하는 날짜가 있는지 확인
            hasMealData = mealDates.includes(dateKey);
          } else if (selectedTag === 'ROUTINE') {
            hasRoutineData = routineDates.includes(dateKey);
          }
          
          const hasData = isCurrent && (selectedTag === 'MEAL' ? hasMealData : hasRoutineData);

          return (
            <button
              key={index}
              onClick={() => handleDateClick(date)}
              className={`aspect-square rounded-token text-sm transition-colors flex flex-col items-center justify-center ${
                isCurrent
                  ? isTodayDate
                    ? 'bg-primary-500 text-bg-root font-bold'
                    : 'bg-bg-surface text-text-main hover:bg-gray-100'
                  : 'text-text-muted'
              }`}
            >
              <span>{date.getDate()}</span>
              {hasData && (
                <span className="mt-1 w-1.5 h-1.5 rounded-full bg-primary-500" />
              )}
            </button>
          );
        })}
      </div>

      {/* Modals */}
      <MealPreviewModal
        isOpen={isMealModalOpen}
        onClose={() => setIsMealModalOpen(false)}
        mealData={mealData}
        date={selectedDate}
      />

      <RoutinePreviewModal
        isOpen={isRoutineModalOpen}
        onClose={() => setIsRoutineModalOpen(false)}
        routineData={routineData}
        date={selectedDate}
      />
    </div>
  );
}

