import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  setTodayRoutine,
  setWeekRoutines,
  setHistoryRoutines,
  setLoading,
  setError,
} from '../store/routinesSlice';
import { routineApi } from '../api/routineApi';

export function useRoutines() {
  const dispatch = useDispatch();
  const { todayRoutine, weekRoutines, historyRoutines, loading, error } = useSelector(
    (state) => state.routines
  );

  const fetchTodayRoutine = async () => {
    try {
      dispatch(setLoading(true));
      const data = await routineApi.getToday();
      dispatch(setTodayRoutine(data));
    } catch (err) {
      dispatch(setError(err.message));
      console.error('오늘의 루틴 조회 실패:', err.message);
      dispatch(setTodayRoutine(null));
    } finally {
      dispatch(setLoading(false));
    }
  };

  const fetchWeekRoutines = async () => {
    try {
      dispatch(setLoading(true));
      const data = await routineApi.getWeek();
      dispatch(setWeekRoutines(Array.isArray(data) ? data : []));
    } catch (err) {
      dispatch(setError(err.message));
      console.error('주간 루틴 조회 실패:', err.message);
      dispatch(setWeekRoutines([]));
    } finally {
      dispatch(setLoading(false));
    }
  };

  const fetchHistoryRoutines = async (bodyPart = null) => {
    try {
      dispatch(setLoading(true));
      const data = await routineApi.getHistory(bodyPart);
      dispatch(setHistoryRoutines(Array.isArray(data) ? data : []));
    } catch (err) {
      dispatch(setError(err.message));
      console.error('기록 조회 실패:', err.message);
      dispatch(setHistoryRoutines([]));
    } finally {
      dispatch(setLoading(false));
    }
  };

  const fetchRoutineByDate = async (date) => {
    try {
      dispatch(setLoading(true));
      const data = await routineApi.getByDate(date);
      return data;
    } catch (err) {
      console.error('날짜별 루틴 조회 실패:', err.message);
      return null;
    } finally {
      dispatch(setLoading(false));
    }
  };

  useEffect(() => {
    fetchTodayRoutine();
    fetchWeekRoutines();
  }, []);

  return {
    todayRoutine,
    weekRoutines,
    historyRoutines,
    loading,
    error,
    fetchTodayRoutine,
    fetchWeekRoutines,
    fetchHistoryRoutines,
    fetchRoutineByDate,
  };
}

