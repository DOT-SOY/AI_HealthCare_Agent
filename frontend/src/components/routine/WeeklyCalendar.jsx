export default function WeeklyCalendar({ routines = [], selectedDate, onDateChange }) {
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const today = new Date();
  
  // 이전 3일 + 오늘 + 앞 3일 (총 7일)
  // selectedDate를 기준으로 주간 캘린더 생성
  // selectedDate가 없으면 오늘 기준으로 생성
  const baseDate = selectedDate || today;

  // baseDate를 중심으로 전후 3일씩 포함한 7일 생성
  const weekDates = [];
  for (let i = -3; i <= 3; i++) {
    const date = new Date(today);
    date.setDate(date.getDate() + i);
  for (let i = -3; i <= 3; i++) {
    const date = new Date(baseDate);
    date.setDate(date.getDate() + i);
    weekDates.push(date);
  }

  const isSelected = (date) => {
    if (!selectedDate) return false;
    return date.toDateString() === selectedDate.toDateString();
  };

  const isToday = (date) => {
    return date.toDateString() === today.toDateString();
  };

  const formatDateKey = (date) => {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  };

  const getRoutineForDate = (date) => {
    const dateStr = formatDateKey(date);
    return routines.find((r) => {
      if (!r?.date) return false;
      const routineDateStr =
        typeof r.date === 'string'
          ? r.date
          : formatDateKey(new Date(r.date));
      return routineDateStr === dateStr;
    });
  };

  return (
    <div className="flex gap-2 mb-6 overflow-x-auto pb-2">
      {weekDates.map((date, index) => {
        const dayName = days[date.getDay()];
        const dayNumber = date.getDate();
        const routine = getRoutineForDate(date);
        const selected = isSelected(date);
        const todayFlag = isToday(date);

        return (
          <button
            key={index}
            type="button"
            onClick={() => onDateChange && onDateChange(date)}
            className={`px-4 py-3 rounded-token text-sm font-medium transition-colors whitespace-nowrap flex flex-col items-center justify-center gap-1 min-w-[70px] border min-h-[4.5rem] ${
              selected
                ? 'bg-primary-500 border-primary-500 text-bg-root'
                : todayFlag
                ? 'bg-bg-card border-primary-500 text-primary-500'
                : 'bg-bg-card border-border-default text-text-main hover:border-primary-500 hover:text-primary-500'
            }`}
          >
            <span className={`text-xs leading-none ${selected ? 'text-bg-root' : 'text-text-muted'}`}>
              {dayName}
            </span>
            <span className={`text-lg font-bold leading-none ${selected ? 'text-bg-root' : todayFlag ? 'text-primary-500' : 'text-text-main'}`}>
              {dayNumber}
            </span>
            {routine && (
              <span className={`w-1.5 h-1.5 rounded-full shrink-0 mt-0.5 ${selected ? 'bg-bg-root' : 'bg-primary-500'}`} />
            )}
          </button>
        );
      })}
    </div>
  );
}

