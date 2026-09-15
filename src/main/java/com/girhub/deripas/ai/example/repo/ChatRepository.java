package com.girhub.deripas.ai.example.repo;

import com.girhub.deripas.ai.example.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<Chat, Long> {

}
