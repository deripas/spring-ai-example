package com.girhub.deripas.ai.example.services;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class DialogService {

    private final ChatClient chatClient;
    private final ChatService chatService;

    public String proceedInteractionSync(Long chatId, String prompt) {
        chatService.requireChat(chatId);
        return chatClient.prompt()
                .user(prompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    public Flux<String> proceedInteractionStreaming(Long chatId, String prompt) {
        return Flux.defer(() -> {
            chatService.requireChat(chatId);
            return chatClient
                    .prompt(prompt)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content();
        });
    }
}
