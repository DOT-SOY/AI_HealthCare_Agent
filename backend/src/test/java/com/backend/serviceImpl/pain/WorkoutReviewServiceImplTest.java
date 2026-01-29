package com.backend.serviceImpl.pain;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.member.Member;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.dto.response.PainAdviceResponse;
import com.backend.repository.routine.RoutineRepository;
import com.backend.service.pain.PainService;
import com.backend.service.pain.WorkoutReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkoutReviewService 테스트")
class WorkoutReviewServiceImplTest {
    
    @Mock
    private RoutineRepository routineRepository;
    
    @Mock
    private PainService painService;
    
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    @InjectMocks
    private WorkoutReviewServiceImpl workoutReviewService;
    
    private Member member;
    private Routine routine;
    private List<Exercise> exercises;
    
    @BeforeEach
    void setUp() {
        member = Member.builder()
            .id(1L)
            .email("test@example.com")
            .pw("encodedPw")
            .name("테스트 회원")
            .gender(Member.Gender.MALE)
            .height(175)
            .weight(70.0)
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
    }
    
    @Test
    @DisplayName("운동 회고 시작 - 모든 운동 완료 시 WebSocket 알림 전송")
    void startWorkoutReview_AllCompleted() {
        // given
        exercises.get(0).setCompleted(true);
        
        when(routineRepository.findByDateAndMemberId(any(LocalDate.class), anyLong()))
            .thenReturn(Optional.of(routine));
        
        // when
        workoutReviewService.startWorkoutReview(1L);
        
        // then
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/workout/review"), any(Object.class));
    }
    
    @Test
    @DisplayName("운동 회고 시작 - 운동 미완료 시 알림 미전송")
    void startWorkoutReview_NotCompleted() {
        // given
        exercises.get(0).setCompleted(false); // 운동 미완료 상태
        
        when(routineRepository.findByDateAndMemberId(any(LocalDate.class), anyLong()))
            .thenReturn(Optional.of(routine));
        
        // when
        workoutReviewService.startWorkoutReview(1L);
        
        // then
        // 모든 운동이 완료되지 않았으므로 WebSocket 알림이 전송되지 않아야 함
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
    
    @Test
    @DisplayName("운동 회고 시작 - 루틴이 없는 경우")
    void startWorkoutReview_NoRoutine() {
        // given
        when(routineRepository.findByDateAndMemberId(any(LocalDate.class), anyLong()))
            .thenReturn(Optional.empty());
        
        // when
        workoutReviewService.startWorkoutReview(1L);
        
        // then
        // 루틴이 없으므로 알림이 전송되지 않아야 함
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
    
    @Test
    @DisplayName("통증 처리 - 오늘 루틴과 관련된 통증 (DB 저장 안 함)")
    void processPainReport_RelatedToExercise() {
        // given
        Long memberId = 1L;
        String bodyPart = "가슴";
        String description = "가슴이 아파요";
        int intensity = 7;
        boolean isRelatedToExercise = true;
        
        // 가슴 운동이 있는 루틴 (CHEST 카테고리)
        Routine chestRoutine = Routine.builder()
            .id(1L)
            .member(member)
            .date(LocalDate.now())
            .title("Push Day")
            .status(RoutineStatus.IN_PROGRESS)
            .exercises(exercises) // exercises에는 CHEST 카테고리 운동이 있음
            .build();
        
        PainAdviceResponse adviceResponse = PainAdviceResponse.builder()
            .bodyPart(bodyPart)
            .count(0) // DB 저장 안 하므로 0
            .level("LOW")
            .advice("가슴 통증 완화 방법입니다.")
            .sources(new ArrayList<>())
            .build();
        
        when(routineRepository.findByDateAndMemberId(any(LocalDate.class), eq(memberId)))
            .thenReturn(Optional.of(chestRoutine));
        when(painService.getPainAdvice(anyString(), eq(0L), anyString()))
            .thenReturn(adviceResponse);
        
        // when
        String result = workoutReviewService.processPainReport(
            memberId, bodyPart, description, intensity, isRelatedToExercise
        );
        
        // then
        assertThat(result).contains("오늘 수행한 운동과 관련된 통증");
        assertThat(result).contains("가슴 통증 완화 방법입니다.");
        // 운동과 연관된 통증은 DB 저장하지 않음
        verify(painService, never()).reportPain(anyLong(), anyString(), anyInt(), anyString(), anyBoolean());
        verify(painService, times(1)).getPainAdvice(bodyPart, 0L, description);
    }
    
    @Test
    @DisplayName("통증 처리 - 오늘 루틴과 관련 없는 통증 (DB 저장 함)")
    void processPainReport_NotRelatedToExercise() {
        // given
        Long memberId = 1L;
        String bodyPart = "무릎";
        String description = "무릎이 아파요";
        int intensity = 6;
        boolean isRelatedToExercise = false;
        
        // 가슴 운동만 있는 루틴 (무릎과 관련 없음)
        when(routineRepository.findByDateAndMemberId(any(LocalDate.class), eq(memberId)))
            .thenReturn(Optional.of(routine));
        
        PainAdviceResponse adviceResponse = PainAdviceResponse.builder()
            .bodyPart(bodyPart)
            .count(1)
            .level("LOW")
            .advice("무릎 통증 완화 방법입니다.")
            .sources(new ArrayList<>())
            .build();
        
        when(painService.reportPain(anyLong(), anyString(), anyInt(), anyString(), eq(false)))
            .thenReturn(1L);
        when(painService.getPainAdvice(anyString(), eq(1L), anyString()))
            .thenReturn(adviceResponse);
        
        // when
        String result = workoutReviewService.processPainReport(
            memberId, bodyPart, description, intensity, isRelatedToExercise
        );
        
        // then
        assertThat(result).contains("직접적인 관련이 없는 통증");
        assertThat(result).contains("자세 교정");
        // 운동과 연관 없는 통증은 DB 저장함
        verify(painService, times(1)).reportPain(memberId, bodyPart, intensity, description, false);
        verify(painService, times(1)).getPainAdvice(bodyPart, 1L, description);
    }
    
    @Test
    @DisplayName("통증 처리 - 에스컬레이션 (3회 이상, 운동과 연관 없는 통증)")
    void processPainReport_Escalation() {
        // given
        Long memberId = 1L;
        String bodyPart = "어깨";
        String description = "어깨가 계속 아파요";
        int intensity = 8;
        boolean isRelatedToExercise = false;
        
        // 가슴 운동만 있는 루틴 (어깨와 관련 없음)
        when(routineRepository.findByDateAndMemberId(any(LocalDate.class), eq(memberId)))
            .thenReturn(Optional.of(routine));
        
        PainAdviceResponse adviceResponse = PainAdviceResponse.builder()
            .bodyPart(bodyPart)
            .count(3)
            .level("HIGH")
            .advice("어깨 통증 완화 방법입니다.")
            .sources(new ArrayList<>())
            .build();
        
        when(painService.reportPain(anyLong(), anyString(), anyInt(), anyString(), eq(false)))
            .thenReturn(3L);
        when(painService.getPainAdvice(anyString(), eq(3L), anyString()))
            .thenReturn(adviceResponse);
        
        // when
        String result = workoutReviewService.processPainReport(
            memberId, bodyPart, description, intensity, isRelatedToExercise
        );
        
        // then
        assertThat(result).contains("⚠️ 경고");
        assertThat(result).contains("3회 발생했습니다");
        assertThat(result).contains("전문의의 진료");
        // 운동과 연관 없는 통증은 DB 저장함
        verify(painService, times(1)).reportPain(memberId, bodyPart, intensity, description, false);
        verify(painService, times(1)).getPainAdvice(bodyPart, 3L, description);
    }
}
