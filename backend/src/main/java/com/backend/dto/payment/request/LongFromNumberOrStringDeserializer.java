package com.backend.dto.payment.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * JSON에서 Long 필드를 숫자 또는 문자열("50000")로 받기 위한 deserializer.
 * 토스 successUrl 리다이렉트 시 amount가 쿼리스트링으로 전달될 수 있어 프론트에서 문자열로 보낼 수 있음.
 */
public class LongFromNumberOrStringDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        switch (p.getCurrentToken()) {
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return p.getLongValue();
            case VALUE_STRING:
                String s = p.getText().trim();
                if (s.isEmpty()) {
                    return null;
                }
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException e) {
                    return null; // @NotNull 검증에서 400 응답
                }
            default:
                return (Long) ctxt.handleUnexpectedToken(Long.class, p);
        }
    }
}
