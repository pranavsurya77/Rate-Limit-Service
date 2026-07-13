package com.example.rate_limit.event;

import lombok.Getter;

@Getter
public class ClientConfigChangedEvent {
    private final String clientKey;

    public ClientConfigChangedEvent(String clientKey) {
        this.clientKey = clientKey;
    }
}
