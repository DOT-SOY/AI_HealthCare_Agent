package com.backend.serviceImpl.pain;

import com.backend.client.PainAdviceClient;
import com.backend.domain.member.Member;
import com.backend.domain.member.Target;
import com.backend.domain.pain.PainLog;
import com.backend.dto.response.PainAdviceResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.pain.PainLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PainService 테스트")
class PainServiceImplTest {
    
    @Mock
    private PainLogRepository painLogRepository;
    
    @Mock
    private MemberRepository memberRepository;
    
    @Mock
    private PainAdviceClient painAdviceClient;
    
    @InjectMocks
    private PainServiceImpl painService;
    
    private Member member;
    
    @BeforeEach
    void setUp() {
        member = Member.builder()
            .id(1L)
            .name("테스트 회원")
            .target(Target.BULK)
            .physicalInfo("{}")
            .build();
    }
    
    @Test
    @DisplayName("통증 보고 - 성공")
    void reportPain_Success() {
        // given
        Long memberId = 1L;
        String area = "어깨";
        int intensity = 7;
        String description = "어깨가 아파요";
        boolean isRelatedToExercise = true;
        
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(painLogRepository.save(any(PainLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(painLogRepository.countByMemberIdAndAreaAndCreatedAtAfter(anyLong(), anyString(), any(LocalDateTime.class)))
            .thenReturn(1L);
        
        // when
        long escalationCount = painService.reportPain(memberId, area, intensity, description, isRelatedToExercise);
        
        // then
        assertThat(escalationCount).isEqualTo(1L);
        verify(memberRepository, times(1)).findById(memberId);
        verify(painLogRepository, times(1)).save(any(PainLog.class));
        verify(painLogRepository, times(1)).countByMemberIdAndAreaAndCreatedAtAfter(anyLong(), anyString(), any(LocalDateTime.class));
    }
    
    @Test
    @DisplayName("통증 보고 - 회원을 찾을 수 없는 경우")
    void reportPain_MemberNotFound() {
        // given
        Long memberId = 999L;
        String area = "어깨";
        int intensity = 7;
        String description = "어깨가 아파요";
        boolean isRelatedToExercise = true;
        
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> painService.reportPain(memberId, area, intensity, description, isRelatedToExercise))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("회원을 찾을 수 없습니다");
        
        verify(painLogRepository, never()).save(any(PainLog.class));
    }
    
    @Test
    @DisplayName("에스컬레이션 카운트 계산 - 최근 7일 내 통증 횟수")
    void calculateEscalationCount() {
        // given
        Long memberId = 1L;
        String area = "어깨";
        
        when(painLogRepository.countByMemberIdAndAreaAndCreatedAtAfter(anyLong(), anyString(), any(LocalDateTime.class)))
            .thenReturn(3L);
        
        // when
        long count = painService.calculateEscalationCount(memberId, area);
        
        // then
        assertThat(count).isEqualTo(3L);
        verify(painLogRepository, times(1)).countByMemberIdAndAreaAndCreatedAtAfter(anyLong(), anyString(), any(LocalDateTime.class));
    }
    
    @Test
    @DisplayName("에스컬레이션 카운트 계산 - 통증이 없는 경우")
    void calculateEscalationCount_NoPain() {
        // given
        Long memberId = 1L;
        String area = "어깨";
        
        when(painLogRepository.countByMemberIdAndAreaAndCreatedAtAfter(anyLong(), anyString(), any(LocalDateTime.class)))
            .thenReturn(0L);
        
        // when
        long count = painService.calculateEscalationCount(memberId, area);
        
        // then
        assertThat(count).isEqualTo(0L);
    }
    
    @Test
    @DisplayName("통증 조언 요청 - 성공")
    void getPainAdvice_Success() {
        // given
        String area = "어깨";
        long count = 2L;
        String description = "어깨가 아파요";
        
        PainAdviceResponse expectedResponse = PainAdviceResponse.builder()
            .bodyPart(area)
            .count((int) count)
            .level("LOW")
            .advice("어깨 통증 완화를 위한 조언입니다.")
            .sources(new ArrayList<>())
            .build();
        
        when(painAdviceClient.requestAdvice(anyString(), anyLong(), anyString()))
            .thenReturn(expectedResponse);
        
        // when
        PainAdviceResponse response = painService.getPainAdvice(area, count, description);
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getBodyPart()).isEqualTo(area);
        assertThat(response.getCount()).isEqualTo((int) count);
        assertThat(response.getLevel()).isEqualTo("LOW");
        assertThat(response.getAdvice()).isEqualTo("어깨 통증 완화를 위한 조언입니다.");
        verify(painAdviceClient, times(1)).requestAdvice(area, count, description);
    }
    
    @Test
    @DisplayName("통증 조언 요청 - HIGH 레벨 (3회 이상)")
    void getPainAdvice_HighLevel() {
        // given
        String area = "어깨";
        long count = 3L;
        String description = "어깨가 계속 아파요";
        
        PainAdviceResponse expectedResponse = PainAdviceResponse.builder()
            .bodyPart(area)
            .count((int) count)
            .level("HIGH")
            .advice("지속적인 통증이 있으니 전문의 진료를 권장합니다.")
            .sources(new ArrayList<>())
            .build();
        
        when(painAdviceClient.requestAdvice(anyString(), anyLong(), anyString()))
            .thenReturn(expectedResponse);
        
        // when
        PainAdviceResponse response = painService.getPainAdvice(area, count, description);
        
        // then
        assertThat(response.getLevel()).isEqualTo("HIGH");
        assertThat(response.getCount()).isEqualTo(3);
    }
}
