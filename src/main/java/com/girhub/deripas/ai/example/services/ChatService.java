package com.girhub.deripas.ai.example.services;

import com.girhub.deripas.ai.example.model.Chat;
import com.girhub.deripas.ai.example.model.ChatEntry;
import com.girhub.deripas.ai.example.repo.ChatEntryRepository;
import com.girhub.deripas.ai.example.repo.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.girhub.deripas.ai.example.model.Role.ASSISTANT;
import static com.girhub.deripas.ai.example.model.Role.USER;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepo;
    private final ChatEntryRepository chatEntryRepo;

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

    @Transactional
    public void writeQA(Long chatId, String question, String answer) {
        chatEntryRepo.saveAll(List.of(
                ChatEntry.builder().chatId(chatId).role(USER).content(question).build(),
                ChatEntry.builder().chatId(chatId).role(ASSISTANT).content(answer).build()
        ));
    }
}
