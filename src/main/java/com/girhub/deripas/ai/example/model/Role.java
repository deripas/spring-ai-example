package com.girhub.deripas.ai.example.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.*;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum Role {

    USER(MessageType.USER) {
        @Override
        Message getMessage(String message) {
            return new UserMessage(message);
        }
    },
    ASSISTANT(MessageType.ASSISTANT) {
        @Override
        Message getMessage(String message) {
            return new AssistantMessage(message);
        }
    },
    SYSTEM(MessageType.SYSTEM) {
        @Override
        Message getMessage(String prompt) {
            return new SystemMessage(prompt);
        }
    };

    private final MessageType messageType;

    public static Role getRole(MessageType messageType) {
        return Arrays.stream(Role.values())
                .filter(role -> role.messageType.equals(messageType))
                .findFirst()
                .orElseThrow();
    }

    abstract Message getMessage(String prompt);

    public String getRole() {
        return messageType.getValue();
    }
}
