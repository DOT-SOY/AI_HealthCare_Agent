package com.backend.service.meal;

import com.backend.domain.meal.Meal;
import com.backend.domain.meal.MealTarget;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealTargetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * meal_target과 meal_schedule 테이블에 2주치 샘플 데이터를 삽입하는 테스트
 * member_id = 9 사용
 */
@SpringBootTest

public class MealDataTest {

    @Autowired
    private MealTargetRepository mealTargetRepository;

    @Autowired
    private MealRepository mealRepository;

    @Test
    public void insertMealDataFor2Weeks() {
        Long memberId = 9L;
        LocalDate today = LocalDate.now();
        
        // 2주치 데이터 생성 (오늘부터 과거 13일)
        List<MealTarget> targets = new ArrayList<>();
        List<Meal> meals = new ArrayList<>();

        // 1주차: 2600kcal, 2주차: 2400kcal (442 비율)
        for (int day = 0; day < 14; day++) {
            LocalDate targetDate = today.minusDays(13 - day);
            boolean isWeek1 = day < 7;
            
            int goalCal = isWeek1 ? 2600 : 2400;
            int goalCarbs = (int) (goalCal * 0.4 / 4); // 442 비율, 탄수화물 1g = 4kcal
            int goalProtein = (int) (goalCal * 0.4 / 4);
            int goalFat = (int) (goalCal * 0.2 / 9); // 지방 1g = 9kcal

            MealTarget target = MealTarget.builder()
                    .userId(memberId)
                    .targetDate(targetDate)
                    .goalType(MemberInfoBody.ExercisePurpose.MAINTAIN)
                    .goalCal(goalCal)
                    .goalCarbs(goalCarbs)
                    .goalProtein(goalProtein)
                    .goalFat(goalFat)
                    .build();
            targets.add(target);

            // 각 날짜마다 3끼 식사 (아침, 점심, 저녁)
            String[] mealTimes = {"BREAKFAST", "LUNCH", "DINNER"};
            String[][] mealMenus = {
                {"오트밀+바나나", "닭가슴살 샐러드", "잡곡밥+된장찌개+불고기"},
                {"계란후라이+토스트", "비빔밥", "연어스테이크+샐러드"},
                {"시리얼+우유", "치킨마요덮밥", "소고기국밥"},
                {"프렌치토스트", "김치볶음밥", "닭볶음탕+밥"},
                {"샌드위치", "파스타", "삼겹살+상추쌈"},
                {"팬케이크", "돈까스", "치킨+콜라"},
                {"죽", "라면+김밥", "회+막국수"}
            };

            int menuIndex = day % 7;
            for (int i = 0; i < 3; i++) {
                String mealTimeStr = mealTimes[i];
                String foodName = mealMenus[menuIndex][i];
                
                // 현실적인 영양소 값 (끼니별로 다르게)
                int cal = 0, carbs = 0, protein = 0, fat = 0;
                if (i == 0) { // 아침
                    cal = 500 + (day % 3) * 50;
                    carbs = cal / 4;
                    protein = cal / 5;
                    fat = cal / 20;
                } else if (i == 1) { // 점심
                    cal = 700 + (day % 3) * 50;
                    carbs = cal / 3;
                    protein = cal / 4;
                    fat = cal / 15;
                } else { // 저녁
                    cal = 600 + (day % 3) * 50;
                    carbs = cal / 4;
                    protein = cal / 4;
                    fat = cal / 18;
                }

                Meal meal = Meal.builder()
                        .userId(memberId)
                        .mealDate(targetDate)
                        .mealTime(Meal.MealTime.valueOf(mealTimeStr))
                        .status(Meal.MealStatus.EATEN)
                        .isAdditional(false)
                        .foodName(foodName)
                        .servingSize("1인분")
                        .calories(cal)
                        .carbs(carbs)
                        .protein(protein)
                        .fat(fat)
                        .originalFoodName(foodName)
                        .originalServingSize("1인분")
                        .originalCalories(cal)
                        .originalCarbs(carbs)
                        .originalProtein(protein)
                        .originalFat(fat)
                        .build();
                meals.add(meal);
            }
        }

        // DB에 저장
        mealTargetRepository.saveAll(targets);
        mealRepository.saveAll(meals);

        System.out.println("✅ meal_target 저장 완료: " + targets.size() + "건");
        System.out.println("✅ meal_schedule 저장 완료: " + meals.size() + "건");
        System.out.println("member_id=" + memberId + "의 2주치 식단 데이터 삽입 완료!");
    }
}

