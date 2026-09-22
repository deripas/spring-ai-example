package com.girhub.deripas.ai.example.services;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class DialogService {

    private final ChatService chatService;
    private final ChatClient chatClient;

    public void proceedInteractionSync(Long chatId, String prompt) {
        chatService.requireChat(chatId);
        final String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        chatService.writeQA(chatId, prompt, answer);
    }

    public Flux<String> proceedInteractionStreaming(Long chatId, String prompt) {
        return Flux.defer(() -> {
            chatService.requireChat(chatId);
            final StringBuilder answer = new StringBuilder();
            return chatClient
                    .prompt(prompt)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content()
                    .doOnNext(answer::append)
                    .concatWith(
                            Mono.<String>fromRunnable(() -> chatService.writeQA(chatId, prompt, answer.toString()))
                                    .subscribeOn(Schedulers.boundedElastic())
                    );
        });
    }
}
