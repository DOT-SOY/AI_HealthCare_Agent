package com.backend.service.meal;

import com.backend.domain.member.Member;
import com.backend.dto.meal.MealDto;
import com.backend.dto.meal.MealTargetDto;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MealServiceTest {

    @Autowired MealService mealService;
    @Autowired MealTargetService mealTargetService;
    @Autowired MealRepository mealRepository;
    @Autowired MemberRepository memberRepository;

    private Member createMember() {
        String email = "meal-test-" + UUID.randomUUID() + "@example.com";
        return memberRepository.save(Member.builder()
                .email(email)
                .pw("pw")
                .name("meal-test-user")
                .gender(Member.Gender.MALE)
                .build());
    }

    @Test
    @DisplayName("MealService.registerAdditionalMeal: 추가 식단이 DB에 저장되고(EATEN/isAdditional=true) 다시 조회된다")
    void registerAdditionalMeal_persistsToDb() {
        Member m = createMember();
        LocalDate date = LocalDate.of(2026, 1, 27);

        MealDto req = MealDto.builder()
                .mealDate(date)
                .mealTime("BREAKFAST")
                .foodName("닭가슴살")
                .servingSize("150g")
                .calories(250)
                .carbs(2)
                .protein(45)
                .fat(6)
                .build();

        MealDto saved = mealService.registerAdditionalMeal(m.getId(), req);

        assertThat(saved.getScheduleId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(m.getId());
        assertThat(saved.getMealDate()).isEqualTo(date);
        assertThat(saved.getMealTime()).isEqualTo("BREAKFAST");
        assertThat(saved.getStatus()).isEqualTo("EATEN");
        assertThat(saved.getIsAdditional()).isTrue();

        var entity = mealRepository.findById(saved.getScheduleId()).orElseThrow();
        assertThat(entity.getUserId()).isEqualTo(m.getId());
        assertThat(entity.getMealDate()).isEqualTo(date);
        assertThat(entity.getMealTime().name()).isEqualTo("BREAKFAST");
        assertThat(entity.getStatus().name()).isEqualTo("EATEN");
        assertThat(entity.getIsAdditional()).isTrue();
        assertThat(entity.getFoodName()).isEqualTo("닭가슴살");
        assertThat(entity.getCalories()).isEqualTo(250);
    }

    @Test
    @DisplayName("MealTargetService.updateTarget/getTargetByDate: 목표가 DB에 저장되고 같은 날짜로 조회된다")
    void updateTarget_thenGetTargetByDate_persistsToDb() {
        Member m = createMember();
        LocalDate date = LocalDate.of(2026, 1, 27);

        MealTargetDto req = MealTargetDto.builder()
                .targetDate(date)
                .goalType("MAINTAIN")
                .goalCal(2000)
                .goalCarbs(250)
                .goalProtein(130)
                .goalFat(60)
                .build();

        MealTargetDto saved = mealTargetService.updateTarget(m.getId(), req);
        assertThat(saved.getTargetId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(m.getId());
        assertThat(saved.getTargetDate()).isEqualTo(date);
        assertThat(saved.getGoalType()).isEqualTo("MAINTAIN");
        assertThat(saved.getGoalCal()).isEqualTo(2000);

        MealTargetDto found = mealTargetService.getTargetByDate(m.getId(), date);
        assertThat(found).isNotNull();
        assertThat(found.getUserId()).isEqualTo(m.getId());
        assertThat(found.getTargetDate()).isEqualTo(date);
        assertThat(found.getGoalCal()).isEqualTo(2000);
        assertThat(found.getGoalProtein()).isEqualTo(130);
    }
}

