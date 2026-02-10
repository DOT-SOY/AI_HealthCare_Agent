package com.backend.service.meal;

import com.backend.domain.meal.Meal;
import com.backend.repository.meal.MealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * "최소 통합 테스트" (미비 항목 보강)
 * - 트랜잭션 커밋 이후 WS publish("/topic/meal/changed/{userId}")가 실제로 호출되는지 검증합니다.
 *
 * 주의:
 * - 테스트에 @Transactional을 걸면 롤백되면서 afterCommit이 실행되지 않으므로(커밋 없음),
 *   이 테스트는 의도적으로 트랜잭션을 걸지 않습니다.
 */
@SpringBootTest
@ActiveProfiles("h2")
class MealWsPublishIntegrationTest {

    @Autowired
    private MealService mealService;

    @Autowired
    private MealRepository mealRepository;

    @SpyBean
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        mealRepository.deleteAll();
    }

    @Test
    void toggleMealTimeSkip_shouldPublishMealChangedTopic_afterCommit() {
        Long userId = 1L;
        LocalDate date = LocalDate.of(2026, 2, 5);

        // Given: lunch planned item exists (non-additional)
        mealRepository.save(Meal.builder()
                .userId(userId)
                .mealDate(date)
                .mealTime(Meal.MealTime.LUNCH)
                .status(Meal.MealStatus.PLANNED)
                .isAdditional(false)
                .foodName("A")
                .calories(100).carbs(10).protein(10).fat(5)
                .originalFoodName("A").originalCalories(100).originalCarbs(10).originalProtein(10).originalFat(5)
                .build());

        // When
        String msg = mealService.toggleMealTimeSkip(userId, date, "LUNCH");

        // Then: business message (sanity) + WS publish happened
        assertThat(msg).contains("생략");

        ArgumentCaptor<String> destination = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        verify(messagingTemplate, atLeastOnce()).convertAndSend(destination.capture(), payload.capture());

        assertThat(destination.getAllValues())
                .anySatisfy(d -> assertThat(d).isEqualTo("/topic/meal/changed/" + userId));
        assertThat(payload.getAllValues())
                .anySatisfy(p -> assertThat(p).isEqualTo("reload"));
    }
}







