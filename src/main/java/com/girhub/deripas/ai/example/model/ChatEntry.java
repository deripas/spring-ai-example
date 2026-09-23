package com.girhub.deripas.ai.example.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.ai.chat.messages.Message;

import java.time.LocalDateTime;

@Entity
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ChatEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content;

    @Enumerated(EnumType.STRING)
    private Role role;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;


    public static ChatEntry toChatEntry(Long chatId, Message message) {
        return ChatEntry.builder()
                .chatId(chatId)
                .role(Role.getRole(message.getMessageType()))
                .content(message.getText())
                .build();
    }

    public Message toMessage() {
        return role.getMessage(content);
    }
}
