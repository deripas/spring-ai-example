package com.girhub.deripas.ai.example.repo;

import com.girhub.deripas.ai.example.model.ChatEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatEntryRepository extends JpaRepository<ChatEntry, Long> {

}
