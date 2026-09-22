package com.girhub.deripas.ai.example.controller;

import com.girhub.deripas.ai.example.model.Chat;
import com.girhub.deripas.ai.example.services.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class ChatAdminController {

    private final ChatService chatService;

    @GetMapping("/")
    public String mainPage(ModelMap model) {
        model.addAttribute("chats", chatService.getAllChats());
        return "chat";
    }

    @GetMapping("/chat/{chatId}")
    public String showChat(@PathVariable Long chatId, ModelMap model) {
        model.addAttribute("chats", chatService.getAllChats());
        model.addAttribute("chat", chatService.getChat(chatId));
        return "chat";
    }

    @PostMapping("/chat/new")
    public String newChat(@RequestParam String title) {
        final Chat chat = chatService.createNewChat(title);
        return "redirect:/chat/" + chat.getId();
    }

    @PostMapping("chat/{chatId}/delete")
    public String deleteChat(@PathVariable Long chatId) {
        chatService.deleteChat(chatId);
        return "redirect:/";
    }
}
