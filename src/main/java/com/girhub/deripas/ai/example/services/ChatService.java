package com.girhub.deripas.ai.example.services;

import com.girhub.deripas.ai.example.model.Chat;
import com.girhub.deripas.ai.example.model.ChatEntry;
import com.girhub.deripas.ai.example.model.Role;
import com.girhub.deripas.ai.example.repo.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepo;

    public List<Chat> getAllChats() {
        return chatRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Chat createNewChat(String title) {
        final Chat chat = Chat.builder()
                .title(title)
                .build();
        return chatRepo.save(chat);
    }

    public Chat getChat(Long chatId) {
        return chatRepo.findById(chatId).orElseThrow();
    }

    public void deleteChat(Long chatId) {
        chatRepo.deleteById(chatId);
    }

    public void addChatEntry(Long chatId, String prompt, Role role) {
        final Chat chat = chatRepo.findById(chatId).orElseThrow();
        final ChatEntry entry = ChatEntry.builder()
                .content(prompt)
                .role(role)
                .build();
        chat.addChatEntry(entry);
        chatRepo.save(chat);
    }
}
