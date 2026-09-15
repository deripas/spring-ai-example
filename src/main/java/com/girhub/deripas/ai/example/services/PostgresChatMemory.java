package com.girhub.deripas.ai.example.services;

import com.girhub.deripas.ai.example.model.Chat;
import com.girhub.deripas.ai.example.model.ChatEntry;
import com.girhub.deripas.ai.example.repo.ChatRepository;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.Comparator;
import java.util.List;

@Builder
public class PostgresChatMemory implements ChatMemory {

    private ChatRepository chatMemoryRepository;

    private int maxMessages;

    @Override
    public void add(@NonNull String conversationId, List<Message> messages) {
        final Chat chat = getChat(conversationId);
        for (Message message : messages) {
            chat.addChatEntry(ChatEntry.toChatEntry(message));
        }
        chatMemoryRepository.save(chat);
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String conversationId) {
        final Chat chat = getChat(conversationId);
        return chat.getHistory().stream()
                .sorted(Comparator.comparing(ChatEntry::getCreatedAt))
                .map(ChatEntry::toMessage)
                .limit(maxMessages)
                .toList();

    }

    @Override
    public void clear(@NonNull String conversationId) {
        //not implemented
    }

    private @NonNull Chat getChat(@NonNull String conversationId) {
        return chatMemoryRepository.findById(Long.valueOf(conversationId)).orElseThrow();
    }
}
