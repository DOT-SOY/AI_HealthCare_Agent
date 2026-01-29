package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PainAdviceResponse {
    private String bodyPart;
    private int count;
    private String level; // LOW, HIGH
    private String advice;
    private List<Map<String, Object>> sources; // RAG 소스
}
