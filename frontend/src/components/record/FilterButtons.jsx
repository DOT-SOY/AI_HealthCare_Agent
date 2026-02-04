const MAIN_FILTERS = ['전체', '상체', '하체'];
const SUB_FILTERS_UPPER = ['팔', '어깨', '가슴', '등', '코어', '복근']; // 상체
const SUB_FILTERS_LOWER = ['둔근', '허벅지', '종아리']; // 하체

export default function FilterButtons({ selectedFilter, onFilterChange }) {
  const showUpperSub = selectedFilter === '상체' || SUB_FILTERS_UPPER.includes(selectedFilter);
  const showLowerSub = selectedFilter === '하체' || SUB_FILTERS_LOWER.includes(selectedFilter);

  return (
    <div className="flex flex-col gap-3 mb-6">
      {/* 1단계: 전체 / 상체 / 하체 */}
      <div className="flex flex-wrap gap-2">
        {MAIN_FILTERS.map((filter) => (
          <button
            key={filter}
            type="button"
            onClick={() => onFilterChange(filter)}
            className={`segment-btn ${selectedFilter === filter ? 'segment-btn-active' : ''}`}
          >
            {filter}
          </button>
        ))}
      </div>

      {/* 2단계: 상체 선택 시 — 팔, 어깨, 가슴, 등, 복근 */}
      {showUpperSub && (
        <div className="flex flex-wrap gap-2 pl-0">
          {SUB_FILTERS_UPPER.map((filter) => (
            <button
              key={filter}
              type="button"
              onClick={() => onFilterChange(filter)}
              className={`segment-btn ${selectedFilter === filter ? 'segment-btn-active' : ''}`}
            >
              {filter}
            </button>
          ))}
        </div>
      )}

      {/* 2단계: 하체 선택 시 — 코어, 둔근, 허벅지, 종아리 */}
      {showLowerSub && (
        <div className="flex flex-wrap gap-2 pl-0">
          {SUB_FILTERS_LOWER.map((filter) => (
            <button
              key={filter}
              type="button"
              onClick={() => onFilterChange(filter)}
              className={`segment-btn ${selectedFilter === filter ? 'segment-btn-active' : ''}`}
            >
              {filter}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
