package com.backend.service.meal.ws;

/**
 * Meal WebSocket publisher.
 *
 * - Publishes /topic/meal/changed/{userId} "reload" messages.
 * - Must publish after DB commit to prevent UI reload fetching stale data.
 */
public interface MealWsPublisher {
    void publishMealChangedAfterCommit(Long userId);
}







