package com.girhub.deripas.ai.example.repo;

import com.girhub.deripas.ai.example.model.ChatEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatEntryRepository extends JpaRepository<ChatEntry, Long> {

    List<ChatEntry> findByChatIdOrderByCreatedAtDescIdDesc(Long chatId, Limit limit);

}
