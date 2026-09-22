package com.girhub.deripas.ai.example.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ChatNotFoundException extends RuntimeException {

    public ChatNotFoundException(Long chatId) {
        super("Chat not found: " + chatId);
    }
}
