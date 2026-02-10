package com.backend.service.meal.dashboard;

import com.backend.dto.meal.MealDashboardDto;

import java.time.LocalDate;

public interface MealDashboardService {
    MealDashboardDto getMealDashboard(Long userId, LocalDate date);
}







