package com.backend.serviceImpl.routine;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.member.Member;
import com.backend.domain.member.Target;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.RoutineResponse;
import com.backend.repository.exercise.ExerciseRepository;
import com.backend.repository.routine.RoutineRepository;
import com.backend.service.pain.WorkoutReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutineService 테스트")
class RoutineServiceImplTest {
    
    @Mock
    private RoutineRepository routineRepository;
    
    @Mock
    private ExerciseRepository exerciseRepository;
    
    @Mock
    private WorkoutReviewService workoutReviewService;
    
    @InjectMocks
    private RoutineServiceImpl routineService;
    
    private Member member;
    private Routine routine;
    private List<Exercise> exercises;
    
    @BeforeEach
    void setUp() {
        member = Member.builder()
            .id(1L)
            .name("테스트 회원")
            .target(Target.BULK)
            .physicalInfo("{}")
            .build();
        
        exercises = new ArrayList<>();
        exercises.add(Exercise.builder()
            .id(1L)
            .name("벤치프레스")
//            .category(ExerciseCategory.CHEST)
            .sets(3)
            .reps(10)
            .weight(80.0)
            .orderIndex(0)
            .completed(false)
            .build());
        
        routine = Routine.builder()
            .id(1L)
            .member(member)
            .date(LocalDate.now())
            .title("Push Day")
            .status(RoutineStatus.IN_PROGRESS)
            .exercises(exercises)
            .build();
        
        exercises.get(0).setRoutine(routine);
    }
    
    @Test
    @DisplayName("오늘 루틴 조회 - 성공")
    void getTodayRoutine_Success() {
        // given
        when(routineRepository.findByDateAndMemberId(any(LocalDate.class), anyLong()))
            .thenReturn(Optional.of(routine));
        
        // when
        RoutineResponse response = routineService.getTodayRoutine(1L);
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.isToday()).isTrue();
        assertThat(response.getExercises()).hasSize(1);
    }
    
    @Test
    @DisplayName("오늘 루틴 조회 - 루틴이 없는 경우")
    void getTodayRoutine_NotFound() {
        // given
        when(routineRepository.findByDateAndMemberId(any(LocalDate.class), anyLong()))
            .thenReturn(Optional.empty());
        
        // when
        RoutineResponse response = routineService.getTodayRoutine(1L);
        
        // then
        assertThat(response).isNull();
    }
    
    @Test
    @DisplayName("주간 루틴 조회")
    void getWeeklyRoutines() {
        // given
        List<Routine> routines = new ArrayList<>();
        routines.add(routine);
        
        Routine pastRoutine = Routine.builder()
            .id(2L)
            .member(member)
            .date(LocalDate.now().minusDays(3))
            .title("Pull Day")
            .status(RoutineStatus.COMPLETED)
            .exercises(new ArrayList<>())
            .build();
        routines.add(pastRoutine);
        
        when(routineRepository.findByMemberIdAndDateBetween(anyLong(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(routines);
        
        // when
        List<RoutineResponse> responses = routineService.getWeeklyRoutines(1L);
        
        // then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).isToday()).isTrue();
    }
    
    @Test
    @DisplayName("루틴 상태 업데이트")
    void updateRoutineStatus() {
        // given
        when(routineRepository.findById(1L)).thenReturn(Optional.of(routine));
        when(routineRepository.save(any(Routine.class))).thenReturn(routine);
        
        // when
        RoutineResponse response = routineService.updateRoutineStatus(1L, "COMPLETED");
        
        // then
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        verify(routineRepository, times(1)).save(any(Routine.class));
    }
    
    @Test
    @DisplayName("운동 완료 토글 - 완료로 변경")
    void toggleExerciseCompleted_ToCompleted() {
        // given
        exercises.get(0).setCompleted(false);
        
        when(routineRepository.findById(1L)).thenReturn(Optional.of(routine));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        ExerciseResponse response = routineService.toggleExerciseCompleted(1L, 1L);
        
        // then
        assertThat(response.isCompleted()).isTrue();
        verify(exerciseRepository, times(1)).save(any(Exercise.class));
    }
    
    @Test
    @DisplayName("운동 완료 토글 - 모든 운동 완료 시 회고 시작")
    void toggleExerciseCompleted_AllCompleted_StartReview() {
        // given
        exercises.get(0).setCompleted(false);
        
        when(routineRepository.findById(1L)).thenReturn(Optional.of(routine));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(invocation -> {
            Exercise ex = invocation.getArgument(0);
            ex.setCompleted(true);
            return ex;
        });
        
        // when
        ExerciseResponse response = routineService.toggleExerciseCompleted(1L, 1L);
        
        // then
        assertThat(response.isCompleted()).isTrue();
        verify(workoutReviewService, times(1)).startWorkoutReview(anyLong());
    }
    
    @Test
    @DisplayName("운동 추가")
    void addExercise() {
        // given
        ExerciseAddRequest request = new ExerciseAddRequest();
        request.setName("데드리프트");
        request.setCategory("BACK");
        request.setSets(3);
        request.setReps(5);
        request.setWeight(120.0);
        
        when(routineRepository.findById(1L)).thenReturn(Optional.of(routine));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(invocation -> {
            Exercise ex = invocation.getArgument(0);
            ex.setId(2L);
            ex.setOrderIndex(1);
            return ex;
        });
        
        // when
        ExerciseResponse response = routineService.addExercise(1L, request);
        
        // then
        assertThat(response.getName()).isEqualTo("데드리프트");
//        assertThat(response.getCategory()).isEqualTo("BACK");
        assertThat(response.getOrderIndex()).isEqualTo(1);
        verify(exerciseRepository, times(1)).save(any(Exercise.class));
    }
    
    @Test
    @DisplayName("운동 수정")
    void updateExercise() {
        // given
        ExerciseUpdateRequest request = new ExerciseUpdateRequest();
        request.setName("벤치프레스 (수정)");
        request.setCategory("CHEST");
        request.setSets(4);
        request.setReps(8);
        request.setWeight(85.0);
        
        when(routineRepository.findById(1L)).thenReturn(Optional.of(routine));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        ExerciseResponse response = routineService.updateExercise(1L, 1L, request);
        
        // then
        assertThat(response.getName()).isEqualTo("벤치프레스 (수정)");
        assertThat(response.getSets()).isEqualTo(4);
        assertThat(response.getWeight()).isEqualTo(85.0);
        verify(exerciseRepository, times(1)).save(any(Exercise.class));
    }
    
    @Test
    @DisplayName("운동 삭제")
    void deleteExercise() {
        // given
        when(routineRepository.findById(1L)).thenReturn(Optional.of(routine));
        
        // when
        routineService.deleteExercise(1L, 1L);
        
        // then
        verify(exerciseRepository, times(1)).delete(any(Exercise.class));
    }
    
    @Test
    @DisplayName("운동 삭제 - 루틴을 찾을 수 없는 경우")
    void deleteExercise_RoutineNotFound() {
        // given
        when(routineRepository.findById(1L)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> routineService.deleteExercise(1L, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("루틴을 찾을 수 없습니다");
        
        verify(exerciseRepository, never()).delete(any(Exercise.class));
    }
    
    @Test
    @DisplayName("운동 삭제 - 운동을 찾을 수 없는 경우")
    void deleteExercise_ExerciseNotFound() {
        // given
        when(routineRepository.findById(1L)).thenReturn(Optional.of(routine));
        
        // when & then
        assertThatThrownBy(() -> routineService.deleteExercise(1L, 999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("운동을 찾을 수 없습니다");
        
        verify(exerciseRepository, never()).delete(any(Exercise.class));
    }
    
    @Test
    @DisplayName("운동 히스토리 조회")
    void getHistory() {
        // given
        List<Routine> routines = new ArrayList<>();
        routines.add(routine);
        
        when(routineRepository.findByMemberIdAndDateBetween(anyLong(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(routines);
        
        // when
        List<RoutineResponse> responses = routineService.getHistory(1L, "어깨");
        
        // then
        assertThat(responses).isNotEmpty();
    }
    
    @Test
    @DisplayName("특정 루틴 상세 조회 - 성공")
    void getRoutineById_Success() {
        // given
        routine.setAiSummary("오늘의 운동 요약");
        when(routineRepository.findById(1L))
            .thenReturn(Optional.of(routine));
        
        // when
        RoutineResponse response = routineService.getRoutineById(1L);
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSummary()).isEqualTo("오늘의 운동 요약");
        assertThat(response.getExercises()).isNotEmpty();
    }
    
    @Test
    @DisplayName("특정 루틴 상세 조회 - 루틴이 없는 경우")
    void getRoutineById_NotFound() {
        // given
        when(routineRepository.findById(999L))
            .thenReturn(Optional.empty());
        
        // when
        RoutineResponse response = routineService.getRoutineById(999L);
        
        // then
        assertThat(response).isNull();
    }
}
