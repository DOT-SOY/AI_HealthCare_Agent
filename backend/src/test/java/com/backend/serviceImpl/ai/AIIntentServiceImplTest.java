package com.backend.serviceImpl.ai;

import com.backend.client.ChatClient;
import com.backend.dto.response.ChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.ai.AIIntentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AIIntentService 테스트")
class AIIntentServiceImplTest {
    
    @Mock
    private ChatClient chatClient;
    
    @InjectMocks
    private AIIntentService.AIIntentServiceImpl aiIntentService;
    
    private ChatResponse chatResponse;
    
    @BeforeEach
    void setUp() {
        chatResponse = new ChatResponse();
    }
    
    @Test
    @DisplayName("의도 분류 - PAIN_REPORT")
    void classifyIntent_PainReport() {
        // given
        String userInput = "어깨가 아파요";
        
        Map<String, Object> entities = new HashMap<>();
        entities.put("body_part", "어깨");
        entities.put("intensity", 7);
        
        chatResponse.setIntent("PAIN_REPORT");
        chatResponse.setEntities(entities);
        chatResponse.setAiAnswer("통증이 감지되었습니다.");
        chatResponse.setRequiresDbCheck(true);
        
        when(chatClient.classifyIntent(anyString())).thenReturn(chatResponse);
        
        // when
        IntentClassificationResult result = aiIntentService.classifyIntent(userInput);
        
        // then
        assertThat(result.getIntent()).isEqualTo("PAIN_REPORT");
        assertThat(result.getEntities()).containsEntry("body_part", "어깨");
        assertThat(result.getEntities()).containsEntry("intensity", 7);
        assertThat(result.getAiAnswer()).isEqualTo("통증이 감지되었습니다.");
        assertThat(result.isRequiresDbCheck()).isTrue();
    }
    
    @Test
    @DisplayName("의도 분류 - WORKOUT_REVIEW (호환성 유지, 향후 Python AI 서버에서 제거 예정)")
    void classifyIntent_WorkoutReview() {
        // given
        String userInput = "오늘 운동이 힘들었어요";
        
        // Python AI 서버에서 WORKOUT_REVIEW를 반환할 수 있지만,
        // 백엔드에서는 GENERAL_CHAT으로 처리됨 (호환성 유지)
        chatResponse.setIntent("WORKOUT_REVIEW");
        chatResponse.setEntities(new HashMap<>());
        chatResponse.setAiAnswer("운동 회고를 기록하겠습니다.");
        chatResponse.setRequiresDbCheck(false);
        
        when(chatClient.classifyIntent(anyString())).thenReturn(chatResponse);
        
        // when
        IntentClassificationResult result = aiIntentService.classifyIntent(userInput);
        
        // then
        assertThat(result.getIntent()).isEqualTo("WORKOUT_REVIEW");
        assertThat(result.getAiAnswer()).isEqualTo("운동 회고를 기록하겠습니다.");
        assertThat(result.isRequiresDbCheck()).isFalse();
    }
    
    @Test
    @DisplayName("의도 분류 - GENERAL_CHAT")
    void classifyIntent_GeneralChat() {
        // given
        String userInput = "안녕하세요";
        
        chatResponse.setIntent("GENERAL_CHAT");
        chatResponse.setEntities(new HashMap<>());
        chatResponse.setAiAnswer("안녕하세요! 무엇을 도와드릴까요?");
        chatResponse.setRequiresDbCheck(false);
        
        when(chatClient.classifyIntent(anyString())).thenReturn(chatResponse);
        
        // when
        IntentClassificationResult result = aiIntentService.classifyIntent(userInput);
        
        // then
        assertThat(result.getIntent()).isEqualTo("GENERAL_CHAT");
        assertThat(result.getAiAnswer()).isEqualTo("안녕하세요! 무엇을 도와드릴까요?");
        assertThat(result.isRequiresDbCheck()).isFalse();
    }
}
