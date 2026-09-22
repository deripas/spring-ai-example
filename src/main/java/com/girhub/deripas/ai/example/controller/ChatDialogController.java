package com.girhub.deripas.ai.example.controller;

import com.girhub.deripas.ai.example.services.DialogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Controller
@RequiredArgsConstructor
public class ChatDialogController {

    private final DialogService dialogService;

    @PostMapping("/chat/{chatId}/entry")
    public String talkToModelSync(@PathVariable Long chatId, @RequestParam String prompt) {
        dialogService.proceedInteractionSync(chatId, prompt);
        return "redirect:/chat/" + chatId;
    }

    @ResponseBody
    @GetMapping(value = "/chat-stream/{chatId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Token> talkToModelStream(@PathVariable Long chatId, @RequestParam String prompt) {
        return dialogService.proceedInteractionStreaming(chatId, prompt)
                .map(Token::new);
    }

    public record Token(String text) {}
}
