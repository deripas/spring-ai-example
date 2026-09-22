package com.girhub.deripas.ai.example.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

import static com.girhub.deripas.ai.example.model.Role.ASSISTANT;
import static com.girhub.deripas.ai.example.model.Role.USER;

@Service
@RequiredArgsConstructor
public class DialogService {

    private final ChatService  chatService;
    private final ChatClient chatClient;

    @Transactional
    public void proceedInteractionSync(Long chatId, String prompt) {
        chatService.addChatEntry(chatId, prompt, USER);
        final String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        chatService.addChatEntry(chatId, answer, ASSISTANT);
    }

    public SseEmitter proceedInteractionStreaming(Long chatId, String prompt) {
        final SseEmitter sseEmitter = new SseEmitter(0L);
        final StringBuilder answer = new StringBuilder();

        chatService.addChatEntry(chatId, prompt, USER);
        chatClient
                .prompt(prompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .chatResponse()
                .subscribe(
                        (ChatResponse response) -> processToken(response, sseEmitter, answer),
                        sseEmitter::completeWithError,
                        () -> {
                            sseEmitter.complete();
                            chatService.addChatEntry(chatId, answer.toString(), ASSISTANT);
                        }
                );
        return sseEmitter;
    }

    @SneakyThrows
    private static void processToken(ChatResponse response, SseEmitter emitter, StringBuilder answer) {
        final Generation result = Objects.requireNonNull(response.getResult());
        final AssistantMessage message = result.getOutput();
        emitter.send(message);
        answer.append(message.getText());
    }
}
