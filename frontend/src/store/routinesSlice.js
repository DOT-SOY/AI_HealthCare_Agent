import { createSlice } from '@reduxjs/toolkit';

const routinesSlice = createSlice({
  name: 'routines',
  initialState: {
    todayRoutine: null,
    weekRoutines: [],
    historyRoutines: [],
    loading: false,
    error: null,
  },
  reducers: {
    setTodayRoutine: (state, action) => {
      state.todayRoutine = action.payload;
    },
    setWeekRoutines: (state, action) => {
      state.weekRoutines = action.payload;
    },
    setHistoryRoutines: (state, action) => {
      state.historyRoutines = action.payload;
    },
    updateRoutineStatus: (state, action) => {
      const { id, status } = action.payload;
      if (state.todayRoutine?.id === id) {
        state.todayRoutine.status = status;
      }
      state.weekRoutines = state.weekRoutines.map(routine =>
        routine.id === id ? { ...routine, status } : routine
      );
    },
    toggleExerciseCompleted: (state, action) => {
      const { routineId, exerciseId } = action.payload;
      
      // todayRoutine 업데이트
      if (state.todayRoutine?.id === routineId) {
        const exercise = state.todayRoutine.exercises?.find(e => e.id === exerciseId);
        if (exercise) {
          exercise.completed = !exercise.completed;
        }
      }
      
      // weekRoutines 업데이트
      const weekRoutine = state.weekRoutines.find(r => r.id === routineId);
      if (weekRoutine) {
        const exercise = weekRoutine.exercises?.find(e => e.id === exerciseId);
        if (exercise) {
          exercise.completed = !exercise.completed;
        }
      }
    },
    updateExerciseCompleted: (state, action) => {
      const { routineId, exerciseId, completed } = action.payload;
      
      // todayRoutine 업데이트
      if (state.todayRoutine?.id === routineId) {
        const exercise = state.todayRoutine.exercises?.find(e => e.id === exerciseId);
        if (exercise) {
          exercise.completed = completed;
        }
      }
      
      // weekRoutines 업데이트
      const weekRoutine = state.weekRoutines.find(r => r.id === routineId);
      if (weekRoutine) {
        const exercise = weekRoutine.exercises?.find(e => e.id === exerciseId);
        if (exercise) {
          exercise.completed = completed;
        }
      }
    },
    addExerciseToRoutine: (state, action) => {
      const { routineId, exercise } = action.payload;
      
      // todayRoutine 업데이트
      if (state.todayRoutine?.id === routineId) {
        if (!state.todayRoutine.exercises) {
          state.todayRoutine.exercises = [];
        }
        state.todayRoutine.exercises.push(exercise);
        // orderIndex 기준으로 정렬
        state.todayRoutine.exercises.sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0));
      }
      
      // weekRoutines 업데이트
      const weekRoutine = state.weekRoutines.find(r => r.id === routineId);
      if (weekRoutine) {
        if (!weekRoutine.exercises) {
          weekRoutine.exercises = [];
        }
        weekRoutine.exercises.push(exercise);
        // orderIndex 기준으로 정렬
        weekRoutine.exercises.sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0));
      }
    },
    updateExerciseInRoutine: (state, action) => {
      const { routineId, exerciseId, exercise } = action.payload;
      
      // todayRoutine 업데이트
      if (state.todayRoutine?.id === routineId) {
        const index = state.todayRoutine.exercises?.findIndex(e => e.id === exerciseId);
        if (index !== undefined && index !== -1) {
          state.todayRoutine.exercises[index] = { ...state.todayRoutine.exercises[index], ...exercise };
        }
      }
      
      // weekRoutines 업데이트
      const weekRoutine = state.weekRoutines.find(r => r.id === routineId);
      if (weekRoutine) {
        const index = weekRoutine.exercises?.findIndex(e => e.id === exerciseId);
        if (index !== undefined && index !== -1) {
          weekRoutine.exercises[index] = { ...weekRoutine.exercises[index], ...exercise };
        }
      }
    },
    removeExerciseFromRoutine: (state, action) => {
      const { routineId, exerciseId } = action.payload;
      
      // todayRoutine 업데이트
      if (state.todayRoutine?.id === routineId) {
        if (state.todayRoutine.exercises) {
          state.todayRoutine.exercises = state.todayRoutine.exercises.filter(e => e.id !== exerciseId);
        }
      }
      
      // weekRoutines 업데이트
      const weekRoutine = state.weekRoutines.find(r => r.id === routineId);
      if (weekRoutine && weekRoutine.exercises) {
        weekRoutine.exercises = weekRoutine.exercises.filter(e => e.id !== exerciseId);
      }
    },
    setLoading: (state, action) => {
      state.loading = action.payload;
    },
    setError: (state, action) => {
      state.error = action.payload;
    },
  },
});

export const {
  setTodayRoutine,
  setWeekRoutines,
  setHistoryRoutines,
  updateRoutineStatus,
  toggleExerciseCompleted,
  updateExerciseCompleted,
  addExerciseToRoutine,
  updateExerciseInRoutine,
  removeExerciseFromRoutine,
  setLoading,
  setError,
} = routinesSlice.actions;

export default routinesSlice.reducer;

