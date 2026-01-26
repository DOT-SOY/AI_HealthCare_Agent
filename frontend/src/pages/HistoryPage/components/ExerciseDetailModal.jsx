import { useState, useEffect, useRef } from 'react';
import { gsap } from 'gsap';

export default function ExerciseDetailModal({ exerciseName, routines, isOpen, onClose }) {
  const [filteredRoutines, setFilteredRoutines] = useState([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const cardsRef = useRef(null);
  const containerRef = useRef(null);

  useEffect(() => {
    if (exerciseName && routines) {
      const filtered = routines
        .filter(routine => 
          routine.exercises?.some(ex => ex.name === exerciseName && ex.completed === true)
        )
        .map(routine => ({
          ...routine,
          exercises: routine.exercises?.filter(ex => ex.name === exerciseName && ex.completed === true)
        }))
        .sort((a, b) => {
          const dateA = typeof a.date === 'string' ? new Date(a.date) : a.date;
          const dateB = typeof b.date === 'string' ? new Date(b.date) : b.date;
          return dateB - dateA; // 최신순 정렬 (첫 번째가 최신)
        });
      
      setFilteredRoutines(filtered);
    } else {
      setFilteredRoutines([]);
    }
  }, [exerciseName, routines]);

  // 모달이 열릴 때마다 항상 최신 기록(첫 번째 카드)으로 리셋
  useEffect(() => {
    if (isOpen) {
      setCurrentIndex(0);
    }
  }, [isOpen]);

  // 카드 애니메이션
  useEffect(() => {
    if (!isOpen || !cardsRef.current || filteredRoutines.length === 0) {
      return;
    }

    const cards = cardsRef.current.querySelectorAll('li');
    if (cards.length === 0) return;

    cards.forEach((card, index) => {
      // 초기 위치: 중앙 기준으로 설정
      gsap.set(card, {
        left: '50%',
        top: '50%',
        xPercent: -50,
        yPercent: -50
      });

      const diff = index - currentIndex;
      
      if (diff === 0) {
        // 현재 카드는 정중앙에 보이도록
        gsap.to(card, {
          x: 0,
          y: 0,
          opacity: 1,
          scale: 1,
          zIndex: 100,
          duration: 0.5,
          ease: "power2.out"
        });
      } else if (Math.abs(diff) <= 2) {
        // 양쪽으로 2개씩 보이도록
        const offset = -diff * 400;
        const scale = 1 - Math.abs(diff) * 0.1;
        const opacity = 1 - Math.abs(diff) * 0.2;
        
        gsap.to(card, {
          x: offset,
          y: 0,
          opacity: Math.max(0.2, opacity),
          scale: Math.max(0.7, scale),
          zIndex: 100 - Math.abs(diff) * 10,
          duration: 0.5,
          ease: "power2.out"
        });
      } else {
        // 더 먼 카드는 숨김
        gsap.to(card, {
          x: diff > 0 ? 1000 : -1000,
          y: 0,
          opacity: 0,
          scale: 0.5,
          zIndex: 1,
          duration: 0.5,
          ease: "power2.out"
        });
      }
    });
  }, [currentIndex, isOpen, filteredRoutines.length]);

  // 스크롤 이벤트 처리
  useEffect(() => {
    if (!isOpen || !containerRef.current || filteredRoutines.length === 0) {
      return;
    }

    const container = containerRef.current;
    let isScrolling = false;

    const handleWheel = (e) => {
      if (isScrolling) return;
      
      e.preventDefault();
      isScrolling = true;

      if (e.deltaY > 0) {
        // 아래로 스크롤 = 과거 기록으로 (Prev)
        setCurrentIndex(prev => {
          if (prev < filteredRoutines.length - 1) {
            return prev + 1;
          }
          return prev;
        });
      } else {
        // 위로 스크롤 = 최신 기록으로 (Next)
        setCurrentIndex(prev => {
          if (prev > 0) {
            return prev - 1;
          }
          return prev;
        });
      }

      setTimeout(() => {
        isScrolling = false;
      }, 500);
    };

    container.addEventListener('wheel', handleWheel, { passive: false });

    return () => {
      container.removeEventListener('wheel', handleWheel);
    };
  }, [isOpen, filteredRoutines.length]);

  const handleNext = () => {
    if (currentIndex > 0) {
      setCurrentIndex(currentIndex - 1);
    }
  };

  const handlePrev = () => {
    if (currentIndex < filteredRoutines.length - 1) {
      setCurrentIndex(currentIndex + 1);
    }
  };

  if (!isOpen) return null;

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
  };

  const formatNumber = (num) => {
    return num.toLocaleString('ko-KR');
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50">
      <div className="w-full h-full flex flex-col">
        {/* 헤더 */}
        <div className="p-6 flex items-center justify-end">
          <button
            onClick={onClose}
            className="text-neutral-400 hover:text-neutral-50 transition-colors"
          >
            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 캐러셀 */}
        {filteredRoutines.length > 0 ? (
          <div 
            ref={containerRef}
            className="flex-1 relative overflow-hidden"
            style={{ minHeight: '600px' }}
          >
            {/* 제목 표시 */}
            <div className="absolute top-8 left-1/2 transform -translate-x-1/2 z-50 text-center">
              <h3 className="text-4xl font-bold text-neon-green">{exerciseName} 기록</h3>
            </div>
            
            <ul 
              ref={cardsRef} 
              className="cards relative w-full h-full"
              style={{ listStyle: 'none', padding: 0, margin: 0 }}
            >
              {filteredRoutines.map((routine, index) => {
                const exercise = routine.exercises?.[0];
                if (!exercise) return null;
                const totalVolume = exercise.sets * exercise.reps * (exercise.weight ?? 0);
                
                return (
                  <li
                    key={`${routine.id}-${index}`}
                    style={{
                      position: 'absolute',
                      width: '24rem',
                      height: '32rem',
                      opacity: 0,
                      zIndex: 10,
                      pointerEvents: 'none'
                    }}
                  >
                    <div
                      className="w-full h-full bg-gradient-to-br from-neutral-800 to-neutral-900 rounded-xl p-8 flex flex-col justify-between border-2 border-neon-green/30 shadow-lg"
                      style={{
                        background: `linear-gradient(135deg, rgba(17, 24, 39, 0.95) 0%, rgba(31, 41, 55, 0.95) 100%)`,
                        boxShadow: '0 0 30px rgba(136, 206, 2, 0.3)',
                      }}
                    >
                      {/* 날짜 */}
                      <div className="text-center">
                        <div className="text-5xl font-bold text-neon-green mb-2">
                          {formatDate(routine.date)}
                        </div>
                        <div className="text-base text-neutral-400">{routine.title}</div>
                      </div>

                      {/* 운동 정보 */}
                      <div className="space-y-6">
                        <div className="bg-neutral-700/50 rounded-lg p-6">
                          <div className="grid grid-cols-2 gap-6 text-base">
                            <div>
                              <span className="text-neutral-400 block mb-2">세트</span>
                              <span className="text-3xl font-bold text-neutral-50">{exercise.sets || 0}</span>
                            </div>
                            <div>
                              <span className="text-neutral-400 block mb-2">횟수</span>
                              <span className="text-3xl font-bold text-neutral-50">{exercise.reps || 0}</span>
                            </div>
                            <div>
                              <span className="text-neutral-400 block mb-2">무게</span>
                              <span className="text-3xl font-bold text-neutral-50">
                                {exercise.weight != null ? `${exercise.weight}kg` : '-'}
                              </span>
                            </div>
                            <div>
                              <span className="text-neutral-400 block mb-2">볼륨</span>
                              <span className="text-3xl font-bold text-neutral-50">{formatNumber(totalVolume)}kg</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
            
            {/* Prev/Next 버튼 */}
            <div className="actions" style={{ position: 'absolute', bottom: '50px', left: '50%', transform: 'translateX(-50%)', zIndex: 1000 }}>
              <button 
                className="prev" 
                onClick={handlePrev}
                disabled={currentIndex === filteredRoutines.length - 1}
                style={{ 
                  display: 'inline-block', 
                  outline: 'none', 
                  padding: '12px 30px', 
                  background: currentIndex === filteredRoutines.length - 1 ? '#333' : '#111', 
                  border: 'solid 2px #88ce02', 
                  color: '#88ce02', 
                  textDecoration: 'none', 
                  borderRadius: '99px', 
                  fontWeight: 600, 
                  cursor: currentIndex === filteredRoutines.length - 1 ? 'not-allowed' : 'pointer', 
                  lineHeight: '18px', 
                  margin: '0 0.5rem',
                  transition: 'all 0.3s',
                  opacity: currentIndex === filteredRoutines.length - 1 ? 0.5 : 1
                }}
                onMouseEnter={(e) => {
                  if (currentIndex < filteredRoutines.length - 1) {
                    e.target.style.background = '#88ce02';
                    e.target.style.color = '#111';
                  }
                }}
                onMouseLeave={(e) => {
                  if (currentIndex < filteredRoutines.length - 1) {
                    e.target.style.background = '#111';
                    e.target.style.color = '#88ce02';
                  }
                }}
              >
                Prev
              </button>
              <button 
                className="next" 
                onClick={handleNext}
                disabled={currentIndex === 0}
                style={{ 
                  display: 'inline-block', 
                  outline: 'none', 
                  padding: '12px 30px', 
                  background: currentIndex === 0 ? '#333' : '#111', 
                  border: 'solid 2px #88ce02', 
                  color: '#88ce02', 
                  textDecoration: 'none', 
                  borderRadius: '99px', 
                  fontWeight: 600, 
                  cursor: currentIndex === 0 ? 'not-allowed' : 'pointer', 
                  lineHeight: '18px', 
                  margin: '0 0.5rem',
                  transition: 'all 0.3s',
                  opacity: currentIndex === 0 ? 0.5 : 1
                }}
                onMouseEnter={(e) => {
                  if (currentIndex > 0) {
                    e.target.style.background = '#88ce02';
                    e.target.style.color = '#111';
                  }
                }}
                onMouseLeave={(e) => {
                  if (currentIndex > 0) {
                    e.target.style.background = '#111';
                    e.target.style.color = '#88ce02';
                  }
                }}
              >
                Next
              </button>
            </div>
          </div>
        ) : (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center text-neutral-400 py-12">
              <p className="text-xl">{exerciseName}에 대한 기록이 없습니다.</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
