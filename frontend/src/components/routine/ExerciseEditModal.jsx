import { useState, useEffect, useRef } from 'react';

// 프로젝트에서 사용하는 운동 목록
const EXERCISE_LIST = [
  { name: '데드리프트', category: 'BACK' },
  { name: '벤치프레스', category: 'CHEST' },
  { name: '오버헤드프레스', category: 'SHOULDER' },
  { name: '바벨 컬', category: 'ARM' },
  { name: '플랭크', category: 'CORE' },
  { name: '행잉레그레이즈', category: 'ABS' },
  { name: '힙쓰러스트', category: 'GLUTE' },
  { name: '스쿼트', category: 'THIGH' },
  { name: '카프레이즈', category: 'CALF' },
];

// 무게가 필요 없는 운동 목록
const NO_WEIGHT_EXERCISES = ['플랭크', '행잉레그레이즈', '카프레이즈'];

export default function ExerciseEditModal({ exercise, isOpen, onClose, onSave }) {
  const [formData, setFormData] = useState({
    name: '',
    sets: 0,
    reps: 0,
    weight: null,
    category: 'BACK',
  });
  const [showDropdown, setShowDropdown] = useState(false);
  const [filteredExercises, setFilteredExercises] = useState(EXERCISE_LIST);
  const inputRef = useRef(null);
  const dropdownRef = useRef(null);

  useEffect(() => {
    if (exercise) {
      // 운동 수정 모드
      let repsValue = exercise.reps;
      if (typeof repsValue === 'string') {
        const match = repsValue.match(/\d+/);
        repsValue = match ? parseInt(match[0]) : 0;
      }
      
      setFormData({
        name: exercise.name || '',
        sets: exercise.sets || 0,
        reps: repsValue || 0,
        weight: exercise.weight ?? null,
        category: exercise.mainTarget || 'BACK', // mainTarget 사용
      });
    } else {
      // 운동 추가 모드
      setFormData({
        name: '',
        sets: 0,
        reps: 0,
        weight: null,
        category: 'BACK',
      });
    }
    setShowDropdown(false);
  }, [exercise, isOpen]);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target) &&
        inputRef.current &&
        !inputRef.current.contains(event.target)
      ) {
        setShowDropdown(false);
      }
    };

    if (showDropdown) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => {
        document.removeEventListener('mousedown', handleClickOutside);
      };
    }
  }, [showDropdown]);

  const handleNameChange = (value) => {
    setFormData(prev => ({ ...prev, name: value }));
    
    // 필터링된 목록 업데이트
    if (value.trim() === '') {
      setFilteredExercises(EXERCISE_LIST);
      setShowDropdown(true);
    } else {
      const filtered = EXERCISE_LIST.filter(ex =>
        ex.name.toLowerCase().includes(value.toLowerCase())
      );
      setFilteredExercises(filtered);
      setShowDropdown(filtered.length > 0);
    }
    
    // 입력한 값이 목록에 정확히 일치하는지 확인
    const matchingExercise = EXERCISE_LIST.find(ex => ex.name === value);
    if (matchingExercise) {
      // 목록에 있는 운동이면 카테고리 자동 설정
      setFormData(prev => ({
        ...prev,
        name: value,
        category: matchingExercise.category,
        weight: NO_WEIGHT_EXERCISES.includes(value) ? null : prev.weight,
      }));
      setShowDropdown(false);
    }
  };

  const handleExerciseSelect = (selectedExercise) => {
    setFormData(prev => ({
      ...prev,
      name: selectedExercise.name,
      category: selectedExercise.category,
      weight: NO_WEIGHT_EXERCISES.includes(selectedExercise.name) ? null : prev.weight,
    }));
    setShowDropdown(false);
    setFilteredExercises(EXERCISE_LIST);
    // 포커스 제거하여 드롭다운이 다시 열리지 않도록
    inputRef.current?.blur();
  };

  const needsWeight = !NO_WEIGHT_EXERCISES.includes(formData.name);

  if (!isOpen) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    const submitData = {
      ...formData,
      weight: needsWeight ? (formData.weight ?? 0) : null,
    };
    onSave(submitData);
    onClose();
  };

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <div 
      className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50"
      onClick={handleBackdropClick}
    >
      <div 
        className="bg-neutral-800 rounded-lg p-6 w-96 max-h-[90vh] relative" 
        style={{ overflow: 'visible' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* X 버튼 */}
        <button
          type="button"
          onClick={onClose}
          className="absolute top-4 right-4 text-neutral-400 hover:text-neutral-50 transition-colors z-10"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
        
        <h2 className="text-xl font-bold text-neutral-50 mb-4">
          {exercise ? '운동 수정' : '운동 추가'}
        </h2>
        
        <form onSubmit={handleSubmit} className="space-y-4" style={{ overflow: 'visible' }} onClick={(e) => e.stopPropagation()}>
          <div className="relative" style={{ zIndex: 10 }}>
            <label className="block text-sm font-medium text-neutral-300 mb-1">운동명</label>
            <div className="relative">
              <input
                ref={inputRef}
                type="text"
                value={formData.name}
                onChange={(e) => handleNameChange(e.target.value)}
                onFocus={(e) => {
                  if (formData.name.trim() === '') {
                    setFilteredExercises(EXERCISE_LIST);
                  }
                  setShowDropdown(true);
                  e.target.style.boxShadow = '0 0 0 2px #88ce02';
                }}
                onBlur={(e) => e.target.style.boxShadow = ''}
                placeholder="운동명을 입력하거나 목록에서 선택하세요"
                className="w-full bg-neutral-700 text-neutral-50 px-4 py-2 rounded-lg focus:outline-none focus:ring-2 pr-10"
                style={{ '--tw-ring-color': '#88ce02' }}
                required
              />
              <button
                type="button"
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  if (formData.name.trim() === '') {
                    setFilteredExercises(EXERCISE_LIST);
                  }
                  setShowDropdown(!showDropdown);
                }}
                className="absolute right-3 top-1/2 transform -translate-y-1/2 text-neutral-400 hover:text-neutral-200 transition-colors cursor-pointer"
              >
                <svg 
                  className={`w-5 h-5 transition-transform ${showDropdown ? 'rotate-180' : ''}`} 
                  fill="none" 
                  stroke="currentColor" 
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              
              {/* 드롭다운 목록 */}
              {showDropdown && filteredExercises.length > 0 && (
                <div
                  ref={dropdownRef}
                  className="absolute w-full mt-1 bg-neutral-700/95 backdrop-blur-sm rounded-lg shadow-lg border border-neutral-600 max-h-60 overflow-y-auto"
                  style={{ zIndex: 1000, top: '100%' }}
                >
                  {filteredExercises.map((ex) => (
                    <button
                      key={ex.name}
                      type="button"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        handleExerciseSelect(ex);
                      }}
                      className="w-full text-left px-4 py-2 text-neutral-50 hover:bg-neutral-600 transition-colors first:rounded-t-lg last:rounded-b-lg"
                    >
                      {ex.name}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
          
          <div className="overflow-visible">
            <div className="grid grid-cols-3 gap-4">
              <div className="relative">
                <label className="block text-sm font-medium text-neutral-300 mb-1">세트</label>
                <input
                  type="number"
                  value={formData.sets}
                  onChange={(e) => setFormData({ ...formData, sets: parseInt(e.target.value) || 0 })}
                  className="w-full bg-neutral-700 text-neutral-50 px-4 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-neutral-800"
                  style={{ '--tw-ring-color': '#88ce02' }}
                  onFocus={(e) => e.target.style.boxShadow = '0 0 0 2px #88ce02'}
                  onBlur={(e) => e.target.style.boxShadow = ''}
                  min="0"
                  required
                />
              </div>

              <div className="relative">
                <label className="block text-sm font-medium text-neutral-300 mb-1">횟수</label>
                <input
                  type="number"
                  value={formData.reps}
                  onChange={(e) => setFormData({ ...formData, reps: parseInt(e.target.value) || 0 })}
                  className="w-full bg-neutral-700 text-neutral-50 px-4 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-neutral-800"
                  style={{ '--tw-ring-color': '#88ce02' }}
                  onFocus={(e) => e.target.style.boxShadow = '0 0 0 2px #88ce02'}
                  onBlur={(e) => e.target.style.boxShadow = ''}
                  min="0"
                  required
                />
              </div>

              <div className="relative">
                <label className="block text-sm font-medium text-neutral-300 mb-1">
                  무게(kg) {!needsWeight && <span className="text-xs text-neutral-500">(선택)</span>}
                </label>
                <input
                  type="number"
                  value={formData.weight ?? ''}
                  onChange={(e) => {
                    const value = e.target.value === '' ? null : parseFloat(e.target.value);
                    setFormData({ ...formData, weight: value });
                  }}
                  className="w-full bg-neutral-700 text-neutral-50 px-4 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-neutral-800"
                  style={{ '--tw-ring-color': '#88ce02' }}
                  onFocus={(e) => e.target.style.boxShadow = '0 0 0 2px #88ce02'}
                  onBlur={(e) => e.target.style.boxShadow = ''}
                  min="0"
                  step="0.5"
                  placeholder={needsWeight ? "0" : "무게 없음"}
                  required={needsWeight}
                  disabled={!needsWeight}
                />
              </div>
            </div>
          </div>

          <div className="flex gap-2 justify-end mt-6">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 bg-neutral-700 text-neutral-300 rounded-lg hover:bg-neutral-600 transition-colors"
            >
              취소
            </button>
            <button
              type="submit"
              className="px-4 py-2 text-neutral-950 rounded-lg transition-colors font-medium"
              style={{ backgroundColor: '#88ce02' }}
              onMouseEnter={(e) => e.target.style.backgroundColor = 'rgba(136, 206, 2, 0.8)'}
              onMouseLeave={(e) => e.target.style.backgroundColor = '#88ce02'}
            >
              저장
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
