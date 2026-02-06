import { useState, useEffect } from 'react';
import { useSelector } from 'react-redux';
import BasicLayout from "../components/layout/BasicLayout";
import { useRoutines } from '../hooks/useRoutines';
import CalendarWidget from '../components/main/CalendarWidget';
import VolumeChart from '../components/main/VolumeChart';
import TodaysFocus from '../components/main/TodaysFocus';
import ExerciseCardList from '../components/main/ExerciseCardList';

function Main() {
  const loginState = useSelector((state) => state.loginSlice);
  const userName = loginState?.name || 'User';
  const { todayRoutine } = useRoutines();
  const [selectedExercise, setSelectedExercise] = useState(null);

  // 루틴이 로드되면 첫 번째 운동을 기본 선택
  useEffect(() => {
    if (todayRoutine?.exercises && todayRoutine.exercises.length > 0) {
      // selectedExercise가 없거나 현재 루틴의 운동이 아닌 경우에만 첫 번째 운동 선택
      if (!selectedExercise || !todayRoutine.exercises.find(ex => ex.id === selectedExercise.id)) {
        setSelectedExercise(todayRoutine.exercises[0]);
      }
    } else {
      setSelectedExercise(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [todayRoutine]);
  useEffect(() => {
    // 데스크톱(1550px 이상)에서만 스크롤 막기
    const handleResize = () => {
      if (window.innerWidth >= 1550) {
        document.body.style.overflow = 'hidden';
      } else {
        document.body.style.overflow = 'unset';
      }
    };
  
    // 초기 설정
    handleResize();
  
    // 리사이즈 이벤트 리스너 추가
    window.addEventListener('resize', handleResize);
  
    return () => {
      window.removeEventListener('resize', handleResize);
      document.body.style.overflow = 'unset';
    };
  }, []);

  return (
    <BasicLayout>
      <div className="w-full bg-bg-root ">
        {/* Welcome Section */}
        <header className="section-header-token mb-8">
          <h1 className="section-title">
            <span className="text-text-main">WELCOME BACK, </span>
            <span className="text-primary-500">{userName.toUpperCase()}</span>
          </h1>
        </header>

        {/* Top Section: Calendar and Chart */}
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_2fr] gap-6 mb-6">
          {/* Calendar Widget */}
          <div className="bg-bg-card rounded-token p-4 border border-border-default">
            <CalendarWidget />
          </div>

          {/* Volume Chart */}
          <div className="bg-bg-card rounded-token p-4 border border-border-default">
            <VolumeChart />
          </div>
        </div>

        {/* Bottom Section: Today's Focus and Exercise Cards */}
        <div className="bg-bg-card rounded-token p-6 border border-border-default">
          <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-6">
            {/* 왼쪽: TODAY'S FOCUS + START SESSION 버튼 */}
            <div className="flex items-start">
              <TodaysFocus 
                routine={todayRoutine} 
                selectedExercise={selectedExercise}
              />
            </div>
            
            {/* 오른쪽: 운동 목록 */}
            <div className="flex-1 overflow-hidden">
              <ExerciseCardList 
                routine={todayRoutine} 
                selectedExercise={selectedExercise}
                onExerciseSelect={setSelectedExercise}
              />
            </div>
          </div>
        </div>
      </div>
    </BasicLayout>
  );
}

export default Main;
