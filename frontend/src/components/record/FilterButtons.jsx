export default function FilterButtons({ selectedFilter, onFilterChange }) {
  const filters = ['전체', '상체', '하체', '팔', '어깨', '가슴', '등', '코어', '복근', '둔근', '허벅지', '종아리'];

  return (
    <div className="flex flex-wrap gap-2 mb-6">
      {filters.map((filter) => (
        <button
          key={filter}
          onClick={() => onFilterChange(filter)}
          className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
            selectedFilter === filter
              ? 'text-neutral-950'
              : 'bg-neutral-800 text-neutral-400 hover:bg-neutral-700'
          }`}
          style={selectedFilter === filter ? { backgroundColor: '#88ce02' } : {}}
        >
          {filter}
        </button>
      ))}
    </div>
  );
}

