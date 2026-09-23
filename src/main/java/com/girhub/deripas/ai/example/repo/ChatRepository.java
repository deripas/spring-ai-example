package com.girhub.deripas.ai.example.repo;

import com.girhub.deripas.ai.example.model.Chat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    @EntityGraph(attributePaths = "history")
    Optional<Chat> findWithHistoryById(Long id);

}
