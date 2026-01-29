export default function WeeklyCalendar({ routines = [], selectedDate, onDateChange }) {
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const today = new Date();
  
  // 최근 7일 생성
  const weekDates = [];
  for (let i = 0; i < 7; i++) {
    const date = new Date(today);
    date.setDate(date.getDate() - i);
    weekDates.push(date);
  }
  weekDates.reverse(); // 오래된 날짜부터

  const isSelected = (date) => {
    if (!selectedDate) return false;
    return date.toDateString() === selectedDate.toDateString();
  };

  const isToday = (date) => {
    return date.toDateString() === today.toDateString();
  };

  const getRoutineForDate = (date) => {
    const dateStr = date.toISOString().split('T')[0];
    return routines.find(r => {
      if (!r.date) return false;
      // date가 문자열이면 그대로 비교, Date 객체면 변환
      const routineDateStr = typeof r.date === 'string' 
        ? r.date 
        : new Date(r.date).toISOString().split('T')[0];
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
            onClick={() => onDateChange && onDateChange(date)}
            className={`px-4 py-3 rounded-lg text-sm font-medium transition-colors whitespace-nowrap flex flex-col items-center gap-1 min-w-[70px] ${
              selected
                ? 'bg-neon-green text-neutral-950'
                : todayFlag
                ? 'bg-neutral-700 text-neon-green border border-neon-green'
                : 'bg-neutral-800 text-neutral-400 hover:bg-neutral-700'
            }`}
          >
            <span className={`text-xs ${selected ? 'text-neutral-950' : 'text-neutral-500'}`}>
              {dayName}
            </span>
            <span className={`text-lg font-bold ${
              selected ? 'text-neutral-950' : todayFlag ? 'text-neon-green' : 'text-neutral-50'
            }`}>
              {dayNumber}
            </span>
            {routine && (
              <span className={`w-1.5 h-1.5 rounded-full mt-1 ${
                selected ? 'bg-neutral-950' : 'bg-neon-green'
              }`} />
            )}
          </button>
        );
      })}
    </div>
  );
}

