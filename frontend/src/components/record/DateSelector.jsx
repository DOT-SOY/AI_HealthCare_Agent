export default function DateSelector({ selectedDate, onDateChange }) {
  const today = new Date();
  const dates = [];
  
  // 최근 7일 생성
  for (let i = 0; i < 7; i++) {
    const date = new Date(today);
    date.setDate(date.getDate() - i);
    dates.push(date);
  }

  const formatDate = (date) => {
    const month = date.getMonth() + 1;
    const day = date.getDate();
    const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
    const weekday = weekdays[date.getDay()];
    return `${month}/${day} (${weekday})`;
  };

  const isSelected = (date) => {
    if (!selectedDate) return false;
    return date.toDateString() === selectedDate.toDateString();
  };

  return (
    <div className="flex gap-2 mb-6 overflow-x-auto pb-2">
      {dates.map((date, index) => (
        <button
          key={index}
          onClick={() => onDateChange(date)}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors whitespace-nowrap ${
            isSelected(date)
              ? 'bg-neon-green text-neutral-950'
              : 'bg-neutral-800 text-neutral-400 hover:bg-neutral-700'
          }`}
        >
          {formatDate(date)}
        </button>
      ))}
    </div>
  );
}


