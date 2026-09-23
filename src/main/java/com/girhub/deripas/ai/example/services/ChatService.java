package com.girhub.deripas.ai.example.services;

import com.girhub.deripas.ai.example.model.Chat;
import com.girhub.deripas.ai.example.repo.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
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
        return chatRepo.findWithHistoryById(chatId).orElseThrow(() -> new ChatNotFoundException(chatId));
    }

    public void requireChat(Long chatId) {
        if (!chatRepo.existsById(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
    }

    public void deleteChat(Long chatId) {
        chatRepo.deleteById(chatId);
    }
}
