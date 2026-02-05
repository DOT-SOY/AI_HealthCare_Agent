import { useState, useEffect, useRef, useCallback } from 'react';
import { gsap } from 'gsap';
import { routineApi } from '../../api/routineApi';
import LoadingModal from '../common/LoadingModal';

export default function ExerciseDetailModal({ exerciseName, isOpen, onClose }) {
  const [routines, setRoutines] = useState([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const cardsRef = useRef(null);
  const containerRef = useRef(null);
  const observerRef = useRef(null);
  const scrollYRef = useRef(0);
  const pageRef = useRef(0); // 최신 페이지 값을 추적

  // 초기 5개 로드
  useEffect(() => {
    if (isOpen && exerciseName) {
      loadInitialRoutines();
    }
    return () => {
      setRoutines([]);
      setCurrentIndex(0);
      setPage(0);
      pageRef.current = 0;
      setHasMore(true);
    };
  }, [isOpen, exerciseName]);

  const loadInitialRoutines = async () => {
    try {
      setLoading(true);
      const data = await routineApi.getRoutinesByExercise(exerciseName, 0, 5);
      const routinesList = data.content || [];
      setRoutines(routinesList);
      setHasMore(!data.last);
      setPage(1);
      pageRef.current = 1;
    } catch (err) {
      console.error('루틴 로드 실패:', err);
      setRoutines([]);
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  };

  // 무한 스크롤: 마지막 카드가 보이면 다음 페이지 로드
  const loadMore = useCallback(async () => {
    if (loading || !hasMore || !exerciseName) return;
    
    const currentPage = pageRef.current;
    
    try {
      setLoading(true);
      const data = await routineApi.getRoutinesByExercise(exerciseName, currentPage, 1);
      
      if (data.content && data.content.length > 0) {
        setRoutines(prev => {
          const existingIds = new Set(prev.map(r => r.id));
          const newItems = data.content.filter(item => !existingIds.has(item.id));
          return newItems.length > 0 ? [...prev, ...newItems] : prev;
        });
        
        const nextPage = currentPage + 1;
        setPage(nextPage);
        pageRef.current = nextPage;
        
        if (data.last) {
          setHasMore(false);
        }
      } else {
        setHasMore(false);
      }
    } catch (err) {
      console.error('추가 루틴 로드 실패:', err);
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  }, [exerciseName, hasMore, loading]);

  // Intersection Observer로 마지막 카드 감지
  useEffect(() => {
    if (!isOpen || !hasMore || routines.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !loading) {
          loadMore();
        }
      },
      { threshold: 0.5 }
    );

    if (observerRef.current) {
      observer.observe(observerRef.current);
    }

    return () => {
      if (observerRef.current) {
        observer.unobserve(observerRef.current);
      }
    };
  }, [isOpen, hasMore, routines.length, loading, loadMore]);

  // 모달이 열릴 때마다 항상 최신 기록(첫 번째 카드)으로 리셋
  useEffect(() => {
    if (isOpen) {
      setCurrentIndex(0);
    }
  }, [isOpen]);

  // 모달 열림 시 배경 스크롤 막기 (position: fixed로 모바일 포함 안정적 차단)
  useEffect(() => {
    if (isOpen) {
      scrollYRef.current = window.scrollY;
      document.body.style.position = 'fixed';
      document.body.style.top = `-${scrollYRef.current}px`;
      document.body.style.left = '0';
      document.body.style.right = '0';
    } else {
      const y = scrollYRef.current;
      document.body.style.position = '';
      document.body.style.top = '';
      document.body.style.left = '';
      document.body.style.right = '';
      window.scrollTo(0, y);
    }
    return () => {
      document.body.style.position = '';
      document.body.style.top = '';
      document.body.style.left = '';
      document.body.style.right = '';
    };
  }, [isOpen]);

  // 카드 애니메이션
  useEffect(() => {
    if (!isOpen || !cardsRef.current || routines.length === 0) {
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
  }, [currentIndex, isOpen, routines.length]);

  // 스크롤 이벤트 처리
  useEffect(() => {
    if (!isOpen || !containerRef.current || routines.length === 0) {
      return;
    }

    const container = containerRef.current;
    let isScrolling = false;

    const handleWheel = (e) => {
      if (isScrolling) return;
      
      e.preventDefault();
      isScrolling = true;

      if (e.deltaY > 0) {
        // 아래로 스크롤 = 과거 기록으로
        setCurrentIndex(prev => {
          const nextIndex = prev + 1;
          // 마지막에서 2개 전일 때 다음 페이지 로드
          if (nextIndex >= routines.length - 2 && hasMore && !loading) {
            loadMore();
          }
          return Math.min(nextIndex, routines.length - 1);
        });
      } else {
        // 위로 스크롤 = 최신 기록으로
        setCurrentIndex(prev => Math.max(prev - 1, 0));
      }

      setTimeout(() => {
        isScrolling = false;
      }, 500);
    };

    container?.addEventListener('wheel', handleWheel, { passive: false });

    return () => {
      container?.removeEventListener('wheel', handleWheel);
    };
  }, [isOpen, routines.length, hasMore, loading, loadMore]);

  const handleNext = () => {
    if (currentIndex > 0) {
      setCurrentIndex(currentIndex - 1);
    }
  };

  const handlePrev = () => {
    if (currentIndex < routines.length - 1) {
      setCurrentIndex(currentIndex + 1);
      // 마지막에서 2개 전일 때 다음 페이지 로드
      if (currentIndex >= routines.length - 2 && hasMore && !loading) {
        loadMore();
      }
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
    <div className="fixed inset-0 bg-bg-root/80 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="w-full h-full flex flex-col">
        {/* 헤더 */}
        <div className="p-6 flex items-center justify-end">
          <button
            onClick={onClose}
            className="text-text-sub hover:text-text-main transition-colors p-1 rounded-token-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
            aria-label="닫기"
          >
            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 캐러셀 */}
        {routines.length > 0 ? (
          <div
            ref={containerRef}
            className="flex-1 relative overflow-hidden min-h-[600px]"
          >
            {/* 제목 표시 */}
            <div className="absolute top-8 left-1/2 -translate-x-1/2 z-50 text-center">
              <h3 className="text-4xl font-bold text-primary-500">{exerciseName} 기록</h3>
            </div>

            <ul
              ref={cardsRef}
              className="cards relative w-full h-full list-none p-0 m-0"
            >
              {routines.map((routine, index) => {
                const exercise = routine.exercises?.[0];
                if (!exercise) return null;
                const totalVolume = exercise.sets * exercise.reps * (exercise.weight ?? 0);

                return (
                  <li
                    key={`${routine.id}-${index}`}
                    ref={index === routines.length - 1 ? observerRef : null}
                    className="absolute w-96 h-[32rem] opacity-0 z-[10] pointer-events-none"
                  >
                    <div className="card-token w-full h-full rounded-token p-8 flex flex-col justify-between border-2 border-primary-500/30 shadow-card-hover">
                      {/* 날짜 */}
                      <div className="text-center">
                        <div className="text-5xl font-bold mb-2 text-primary-500">
                          {formatDate(routine.date)}
                        </div>
                        <div className="text-base text-text-sub">{routine.title}</div>
                      </div>

                      {/* 운동 정보 */}
                      <div className="space-y-6">
                        <div className="bg-bg-surface rounded-token p-6">
                          <div className="grid grid-cols-2 gap-6 text-base">
                            <div>
                              <span className="text-text-sub block mb-2">세트</span>
                              <span className="text-3xl font-bold text-text-main">{exercise.sets || 0}</span>
                            </div>
                            <div>
                              <span className="text-text-sub block mb-2">횟수</span>
                              <span className="text-3xl font-bold text-text-main">{exercise.reps || 0}</span>
                            </div>
                            <div>
                              <span className="text-text-sub block mb-2">무게</span>
                              <span className="text-3xl font-bold text-text-main">
                                {exercise.weight != null ? `${exercise.weight}kg` : '-'}
                              </span>
                            </div>
                            <div>
                              <span className="text-text-sub block mb-2">볼륨</span>
                              <span className="text-3xl font-bold text-text-main">{formatNumber(totalVolume)}kg</span>
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
            <div className="absolute bottom-12 left-1/2 -translate-x-1/2 z-[1000] flex gap-2">
              <button
                type="button"
                onClick={handlePrev}
                disabled={currentIndex === routines.length - 1}
                className="px-6 py-3 rounded-full font-semibold min-w-[100px] border-2 border-primary-500 bg-bg-card text-primary-500 transition-all duration-200 hover:bg-primary-500 hover:text-bg-root disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-bg-surface disabled:border-border-default disabled:text-text-sub disabled:hover:bg-bg-surface disabled:hover:text-text-sub focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
              >
                Prev
              </button>
              <button
                type="button"
                onClick={handleNext}
                disabled={currentIndex === 0}
                className="px-6 py-3 rounded-full font-semibold min-w-[100px] border-2 border-primary-500 bg-bg-card text-primary-500 transition-all duration-200 hover:bg-primary-500 hover:text-bg-root disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-bg-surface disabled:border-border-default disabled:text-text-sub disabled:hover:bg-bg-surface disabled:hover:text-text-sub focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
              >
                Next
              </button>
            </div>
          </div>
        ) : (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center py-12">
              {loading ? (
                <LoadingModal isOpen={true} message="로딩 중..." />
              ) : (
                <p className="text-xl">{exerciseName}에 대한 기록이 없습니다.</p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
