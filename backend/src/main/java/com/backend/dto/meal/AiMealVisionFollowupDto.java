package com.backend.dto.meal;

import lombok.*;

import java.util.Map;

public class AiMealVisionFollowupDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String userText;
        private Map<String, Object> analyzedFood;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String operation;   // ADD|REPLACE|CANCEL|ASK
        private String mealTime;    // BREAKFAST|LUNCH|DINNER|null
        private String assistantReply;
    }
}



