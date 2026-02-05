export default function WeeklyCalendar({ routines = [], selectedDate, onDateChange }) {
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const today = new Date();
  
  // 이전 3일 + 오늘 + 앞으로 3일 (총 7일)
  const weekDates = [];
  for (let i = -3; i <= 3; i++) {
    const date = new Date(today);
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
                ? 'text-neutral-950'
                : todayFlag
                ? 'bg-neutral-700 border'
                : 'bg-neutral-800 text-neutral-400 hover:bg-neutral-700'
            }`}
            style={selected 
              ? { backgroundColor: '#88ce02' }
              : todayFlag
              ? { color: '#88ce02', borderColor: '#88ce02' }
              : {}}
          >
            <span className={`text-xs ${selected ? 'text-neutral-950' : 'text-neutral-500'}`}>
              {dayName}
            </span>
            <span className={`text-lg font-bold ${
              selected ? 'text-neutral-950' : 'text-neutral-50'
            }`}
            style={!selected && todayFlag ? { color: '#88ce02' } : {}}
            >
              {dayNumber}
            </span>
            {routine && (
              <span className={`w-1.5 h-1.5 rounded-full mt-1 ${
                selected ? 'bg-neutral-950' : ''
              }`}
              style={!selected ? { backgroundColor: '#88ce02' } : {}}
              />
            )}
          </button>
        );
      })}
    </div>
  );
}

