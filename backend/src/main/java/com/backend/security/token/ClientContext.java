package com.backend.security.token;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClientContext {
    private final String userAgentHash;
    private final String ipHint;
}


