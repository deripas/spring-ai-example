package com.girhub.deripas.ai.example.controller;

import com.girhub.deripas.ai.example.services.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class StreamingChatController {

    private final ChatService chatService;

    @GetMapping(value = "/chat-stream/{chatId}",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter talkToModel(@PathVariable Long chatId, @RequestParam String userPrompt){
        return chatService.proceedInteractionWithStreaming(chatId,userPrompt);
    }
}
