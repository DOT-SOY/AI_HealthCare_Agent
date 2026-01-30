package com.backend.service.meal;

import com.backend.domain.meal.Meal;
import com.backend.domain.meal.MealTarget;
import com.backend.domain.member.Member;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealTargetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * 식단 더미데이터 생성 테스트.
 * DB에 있는 모든 회원에게 한 달치 식단 데이터를 생성합니다.
 * 
 * 생성 데이터:
 * - MealTarget: 각 회원마다 한 달치 목표 칼로리/영양소 (목표 타입과 목표치는 한 달 내내 동일)
 * - Meal: 각 회원마다 한 달치 식단 스케줄 (아침, 점심, 저녁)
 *   - 실제 섭취 영양소는 목표치보다 최대 1300kcal 이상 적게 들쑥날쑥하게 생성
 * 
 * 사용 방법:
 * 1. IDE에서 이 테스트를 실행
 * 2. 또는 Gradle: ./gradlew test --tests "com.backend.service.meal.MealDummyDataGeneratorTest.generateMealDummyData"
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("식단 더미데이터 생성")
class MealDummyDataGeneratorTest {

    private static final String[] FOOD_NAMES = {
        "닭가슴살", "계란", "현미밥", "고구마", "브로콜리",
        "연어", "두부", "귀리", "바나나", "사과",
        "오트밀", "아보카도", "견과류", "요거트", "치즈",
        "샐러드", "스무디", "프로틴 쉐이크", "견과류 믹스", "훈제닭가슴살"
    };

    private static final String[] SERVING_SIZES = {
        "100g", "150g", "200g", "1인분", "2인분",
        "1컵", "1조각", "50g", "80g", "120g"
    };

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private MealTargetRepository mealTargetRepository;

    private final Random random = new Random();

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("모든 회원에게 한 달치 식단 데이터 생성")
    void generateMealDummyData() {
        // 1. 모든 회원 조회
        List<Member> members = memberRepository.findAll();
        if (members.isEmpty()) {
            throw new IllegalStateException("식단 더미데이터 생성에는 최소 1명의 회원이 필요합니다.");
        }

        System.out.println("총 " + members.size() + "명의 회원에게 식단 데이터를 생성합니다.");

        // 2. 한 달치 날짜 범위 설정 (오늘부터 과거 30일)
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(30);

        // 3. 각 회원마다 데이터 생성
        for (Member member : members) {
            Long userId = member.getId();
            System.out.println("회원 ID " + userId + " (" + member.getEmail() + ") 데이터 생성 중...");

            // 목표 타입 랜덤 선택 (한 달 내내 동일하게 유지)
            MemberInfoBody.ExercisePurpose goalType = MemberInfoBody.ExercisePurpose.values()[
                random.nextInt(MemberInfoBody.ExercisePurpose.values().length)
            ];

            // 목표 칼로리/영양소 한 번만 설정 (한 달 내내 동일하게 유지)
            int goalCal;
            switch (goalType) {
                case DIET:
                    goalCal = 1500 + random.nextInt(300); // 1500-1800
                    break;
                case MAINTAIN:
                    goalCal = 2000 + random.nextInt(400); // 2000-2400
                    break;
                case BULK_UP:
                    goalCal = 2500 + random.nextInt(500); // 2500-3000
                    break;
                default:
                    goalCal = 2000;
            }

            // 영양소 비율 계산 (탄수화물 50%, 단백질 25%, 지방 25%)
            int goalCarbs = (int) (goalCal * 0.5 / 4); // 1g = 4kcal
            int goalProtein = (int) (goalCal * 0.25 / 4);
            int goalFat = (int) (goalCal * 0.25 / 9); // 1g = 9kcal

            // 각 날짜마다 데이터 생성
            for (int dayOffset = 0; dayOffset <= 30; dayOffset++) {
                LocalDate targetDate = startDate.plusDays(dayOffset);

                // MealTarget 생성 (하루에 1개, 목표치는 동일하게)
                createMealTarget(userId, targetDate, goalType, goalCal, goalCarbs, goalProtein, goalFat);

                // Meal 생성 (하루에 3개: 아침, 점심, 저녁)
                // 실제 섭취 영양소는 목표치보다 최대 1300kcal 이상 적게 들쑥날쑥하게 생성
                createMealsForDay(userId, targetDate, goalCal);
            }

            System.out.println("회원 ID " + userId + " 데이터 생성 완료 (31일치)");
        }

        System.out.println("모든 회원의 식단 데이터 생성이 완료되었습니다!");
    }

    private void createMealTarget(Long userId, LocalDate targetDate, 
                                  MemberInfoBody.ExercisePurpose goalType,
                                  int goalCal, int goalCarbs, int goalProtein, int goalFat) {
        // 이미 존재하는지 확인
        if (mealTargetRepository.findByUserIdAndTargetDate(userId, targetDate).isPresent()) {
            return; // 이미 있으면 스킵
        }

        MealTarget target = MealTarget.builder()
                .userId(userId)
                .targetDate(targetDate)
                .goalType(goalType)
                .goalCal(goalCal)
                .goalCarbs(goalCarbs)
                .goalProtein(goalProtein)
                .goalFat(goalFat)
                .aiFeedback("더미 데이터: " + goalType.getDescription() + " 목표로 설정되었습니다.")
                .build();

        mealTargetRepository.save(target);
    }

    private void createMealsForDay(Long userId, LocalDate mealDate, int goalCal) {
        // 실제 섭취 칼로리는 목표치보다 최대 1300kcal 이상 적게 (들쑥날쑥하게)
        int minCal = Math.max(200, goalCal - 1300); // 최소 200kcal는 보장
        int maxCal = goalCal; // 최대는 목표치
        
        // 하루 총 섭취 칼로리 랜덤 생성 (들쑥날쑥하게)
        int totalDailyCal = minCal + random.nextInt(maxCal - minCal + 1);
        
        // 아침, 점심, 저녁에 칼로리 분배 (들쑥날쑥하게)
        int breakfastCal = 200 + random.nextInt(400); // 200-600
        int lunchCal = 300 + random.nextInt(500); // 300-800
        int dinnerCal = 300 + random.nextInt(500); // 300-800
        
        // 총합이 totalDailyCal에 맞도록 조정
        int currentTotal = breakfastCal + lunchCal + dinnerCal;
        if (currentTotal != totalDailyCal) {
            int diff = totalDailyCal - currentTotal;
            // 차이를 랜덤하게 분배
            int breakfastAdjust = diff / 3 + random.nextInt(Math.max(1, Math.abs(diff) / 3));
            int lunchAdjust = diff / 3 + random.nextInt(Math.max(1, Math.abs(diff) / 3));
            int dinnerAdjust = diff - breakfastAdjust - lunchAdjust;
            
            breakfastCal = Math.max(100, breakfastCal + breakfastAdjust);
            lunchCal = Math.max(100, lunchCal + lunchAdjust);
            dinnerCal = Math.max(100, dinnerCal + dinnerAdjust);
        }
        
        // 각 끼니별로 Meal 생성
        Meal.MealTime[] mealTimes = {
            Meal.MealTime.BREAKFAST,
            Meal.MealTime.LUNCH,
            Meal.MealTime.DINNER
        };
        
        int[] mealCalories = { breakfastCal, lunchCal, dinnerCal };
        
        for (int i = 0; i < mealTimes.length; i++) {
            Meal.MealTime mealTime = mealTimes[i];
            int calories = mealCalories[i];
            
            // 이미 존재하는지 확인
            if (mealRepository.findByUserIdAndMealDateAndMealTime(userId, mealDate, mealTime).isPresent()) {
                continue; // 이미 있으면 스킵
            }

            // 랜덤 음식 정보 생성
            String foodName = FOOD_NAMES[random.nextInt(FOOD_NAMES.length)];
            String servingSize = SERVING_SIZES[random.nextInt(SERVING_SIZES.length)];

            // 영양소 계산 (들쑥날쑥하게 - 탄수화물 40-60%, 단백질 20-30%, 지방 15-25%)
            double carbsRatio = 0.4 + random.nextDouble() * 0.2; // 40-60%
            double proteinRatio = 0.2 + random.nextDouble() * 0.1; // 20-30%
            double fatRatio = 0.15 + random.nextDouble() * 0.1; // 15-25%
            
            int carbs = (int) (calories * carbsRatio / 4);
            int protein = (int) (calories * proteinRatio / 4);
            int fat = (int) (calories * fatRatio / 9);

            // 상태 랜덤 (PLANNED, EATEN, SKIPPED)
            Meal.MealStatus status;
            double statusRand = random.nextDouble();
            if (statusRand < 0.7) {
                status = Meal.MealStatus.EATEN; // 70% 확률로 섭취
            } else if (statusRand < 0.9) {
                status = Meal.MealStatus.PLANNED; // 20% 확률로 계획
            } else {
                status = Meal.MealStatus.SKIPPED; // 10% 확률로 건너뜀
            }

            Meal meal = Meal.builder()
                    .userId(userId)
                    .mealDate(mealDate)
                    .mealTime(mealTime)
                    .status(status)
                    .isAdditional(false)
                    .foodName(foodName)
                    .servingSize(servingSize)
                    .calories(calories)
                    .carbs(carbs)
                    .protein(protein)
                    .fat(fat)
                    // Original 정보도 동일하게 설정
                    .originalFoodName(foodName)
                    .originalServingSize(servingSize)
                    .originalCalories(calories)
                    .originalCarbs(carbs)
                    .originalProtein(protein)
                    .originalFat(fat)
                    .build();

            mealRepository.save(meal);
        }
    }
}

