package com.backend.service.meal.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class MealWsPublisherImpl implements MealWsPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishMealChangedAfterCommit(Long userId) {
        if (userId == null) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend("/topic/meal/changed/" + userId, "reload");
                }
            });
            return;
        }

        messagingTemplate.convertAndSend("/topic/meal/changed/" + userId, "reload");
    }
}







