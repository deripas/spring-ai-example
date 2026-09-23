package com.girhub.deripas.ai.example.services;

import com.girhub.deripas.ai.example.model.ChatEntry;
import com.girhub.deripas.ai.example.repo.ChatEntryRepository;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.domain.Limit;

import java.util.List;

@Builder
public class PostgresChatMemory implements ChatMemory {

    private final ChatEntryRepository chatEntryRepository;
    private final int maxMessages;

    @Override
    public void add(@NonNull String conversationId, List<Message> messages) {
        final Long chatId = chatId(conversationId);
        final List<ChatEntry> entries = messages.stream()
                .map(message -> ChatEntry.toChatEntry(chatId, message))
                .toList();
        chatEntryRepository.saveAll(entries);
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String conversationId) {
        return chatEntryRepository.findByChatIdOrderByCreatedAtDescIdDesc(chatId(conversationId), Limit.of(maxMessages))
                .reversed()
                .stream()
                .map(ChatEntry::toMessage)
                .toList();
    }

    @Override
    public void clear(@NonNull String conversationId) {
        //not implemented
    }

    private static @NonNull Long chatId(@NonNull String conversationId) {
        return Long.valueOf(conversationId);
    }
}
