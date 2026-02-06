export default function WeeklyCalendar({ routines = [], selectedDate, onDateChange }) {
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const today = new Date();
  const baseDate = selectedDate || today;

  // 이전 3일 + 기준일 + 앞으로 3일 (총 7일)
  const weekDates = [];
  for (let i = -3; i <= 3; i++) {
    const date = new Date(baseDate);
    date.setDate(baseDate.getDate() + i);
    weekDates.push(date);
  }

  const isSelected = (date) => {
    if (!selectedDate) return false;
    return date.toDateString() === selectedDate.toDateString();
  };

  const isToday = (date) => {
    return date.toDateString() === today.toDateString();
  };

  const getRoutineForDate = (date) => {
    // 로컬 기준 YYYY-MM-DD 문자열로 통일해서 비교 (UTC 변환으로 인한 1일 차이 방지)
    const toLocalDateKey = (d) => {
      const dd = d instanceof Date ? d : new Date(d);
      const y = dd.getFullYear();
      const m = String(dd.getMonth() + 1).padStart(2, '0');
      const day = String(dd.getDate()).padStart(2, '0');
      return `${y}-${m}-${day}`;
    };

    const dateKey = toLocalDateKey(date);

    return routines.find((r) => {
      if (!r.date) return false;
      const routineKey =
        typeof r.date === 'string'
          ? (r.date.includes('T') ? r.date.split('T')[0] : r.date)
          : toLocalDateKey(r.date);
      return routineKey === dateKey;
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
            onClick={() => onDateChange && onDateChange(date)}
            className={`px-4 py-3 rounded-lg text-sm font-medium transition-colors whitespace-nowrap flex flex-col items-center gap-1 min-w-[70px] ${
              selected
                ? 'bg-primary-500 text-bg-root'
                : todayFlag
                ? 'bg-bg-surface border border-primary-500 text-primary-500'
                : 'bg-bg-card text-text-muted hover:bg-bg-surface'
            }`}
          >
            <span className={`text-xs ${selected ? 'text-bg-root' : 'text-text-muted'}`}>
              {dayName}
            </span>
            <span className={`text-lg font-bold ${
              selected ? 'text-bg-root' : todayFlag ? 'text-primary-500' : 'text-text-main'
            }`}>
              {dayNumber}
            </span>
            {routine && (
              <span className={`w-1.5 h-1.5 rounded-full mt-1 ${
                selected ? 'bg-bg-root' : 'bg-primary-500'
              }`} />
            )}
          </button>
        );
      })}
    </div>
  );
}
