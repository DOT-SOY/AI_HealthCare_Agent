import { useDispatch, useSelector } from 'react-redux';
import { 
  setTodayRoutine, 
  setWeekRoutines, 
  toggleExerciseCompleted, 
  updateExerciseCompleted,
  addExerciseToRoutine,
  updateExerciseInRoutine,
  removeExerciseFromRoutine
} from '../store/routinesSlice';
import { exerciseApi } from '../api/exerciseApi';
import { routineApi } from '../api/routineApi';

export function useExercises() {
  const dispatch = useDispatch();
  const { todayRoutine, weekRoutines } = useSelector((state) => state.routines);

  const refreshTodayRoutine = async () => {
    try {
      const data = await routineApi.getToday();
      dispatch(setTodayRoutine(data));
    } catch (error) {
      console.error('오늘의 루틴 갱신 실패:', error);
    }
  };

  const refreshWeekRoutines = async () => {
    try {
      const data = await routineApi.getWeek();
      dispatch(setWeekRoutines(data));
    } catch (error) {
      console.error('주간 루틴 갱신 실패:', error);
    }
  };

  const toggleCompleted = async (routineId, exerciseId) => {
    try {
      // 1. Optimistic Update: 즉시 UI 업데이트
      dispatch(toggleExerciseCompleted({ routineId, exerciseId }));
      
      // 2. 서버에 요청
      const response = await exerciseApi.toggleCompleted(routineId, exerciseId);
      
      // 3. 서버 응답으로 정확한 상태 반영 (서버의 실제 completed 값으로 동기화)
      dispatch(updateExerciseCompleted({ 
        routineId, 
        exerciseId, 
        completed: response.completed 
      }));
      
      return response;
    } catch (error) {
      // 4. 실패 시 롤백 (다시 토글하여 원래 상태로)
      dispatch(toggleExerciseCompleted({ routineId, exerciseId }));
      console.error('운동 완료 토글 실패:', error);
      throw error;
    }
  };

  const addExercise = async (routineId, exercise) => {
    // 임시 운동 ID 생성 (롤백용)
    const tempExerciseId = `temp-${Date.now()}`;
    const tempExercise = {
      id: tempExerciseId,
      ...exercise,
      completed: false,
      orderIndex: 999, // 임시로 마지막에 배치
    };
    
    try {
      // 1. Optimistic Update: 임시 운동 추가
      dispatch(addExerciseToRoutine({ routineId, exercise: tempExercise }));
      
      // 2. 서버에 요청
      const response = await exerciseApi.add(routineId, exercise);
      
      // 3. 서버 응답으로 임시 운동 교체
      dispatch(removeExerciseFromRoutine({ routineId, exerciseId: tempExerciseId }));
      dispatch(addExerciseToRoutine({ routineId, exercise: response }));
      
      return response;
    } catch (error) {
      // 4. 실패 시 롤백 (임시 운동 제거)
      dispatch(removeExerciseFromRoutine({ routineId, exerciseId: tempExerciseId }));
      console.error('운동 추가 실패:', error);
      throw error;
    }
  };

  const updateExercise = async (routineId, exerciseId, exercise) => {
    // 이전 운동 상태 저장 (롤백용)
    const routine = todayRoutine?.id === routineId ? todayRoutine : weekRoutines.find(r => r.id === routineId);
    const previousExercise = routine?.exercises?.find(e => e.id === exerciseId);
    
    try {
      // 1. Optimistic Update: 즉시 UI 업데이트
      const exerciseData = {
        name: exercise.name,
        category: exercise.mainTarget || 'CHEST', // mainTarget 사용
        sets: exercise.sets,
        reps: exercise.reps,
        weight: exercise.weight,
      };
      dispatch(updateExerciseInRoutine({ routineId, exerciseId, exercise: exerciseData }));
      
      // 2. 서버에 요청
      const response = await exerciseApi.update(routineId, exerciseId, exerciseData);
      
      // 3. 서버 응답으로 정확한 상태 반영
      dispatch(updateExerciseInRoutine({ routineId, exerciseId, exercise: response }));
      
      return response;
    } catch (error) {
      // 4. 실패 시 롤백 (이전 상태로 복원)
      if (previousExercise) {
        dispatch(updateExerciseInRoutine({ routineId, exerciseId, exercise: previousExercise }));
      }
      console.error('운동 수정 실패:', error);
      throw error;
    }
  };

  const deleteExercise = async (routineId, exerciseId) => {
    // 삭제될 운동 정보 저장 (롤백용)
    const routine = todayRoutine?.id === routineId ? todayRoutine : weekRoutines.find(r => r.id === routineId);
    const deletedExercise = routine?.exercises?.find(e => e.id === exerciseId);
    
    try {
      // 1. Optimistic Update: 즉시 UI에서 제거
      dispatch(removeExerciseFromRoutine({ routineId, exerciseId }));
      
      // 2. 서버에 요청
      await exerciseApi.delete(routineId, exerciseId);
    } catch (error) {
      // 3. 실패 시 롤백 (운동 다시 추가)
      if (deletedExercise) {
        dispatch(addExerciseToRoutine({ routineId, exercise: deletedExercise }));
      }
      console.error('운동 삭제 실패:', error);
      throw error;
    }
  };

  return {
    toggleCompleted,
    addExercise,
    updateExercise,
    deleteExercise,
  };
}

