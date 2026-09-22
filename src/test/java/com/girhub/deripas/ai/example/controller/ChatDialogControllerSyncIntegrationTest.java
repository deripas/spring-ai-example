package com.girhub.deripas.ai.example.controller;

import com.girhub.deripas.ai.example.AbstractIntegrationTest;
import com.girhub.deripas.ai.example.model.Chat;
import com.girhub.deripas.ai.example.model.ChatEntry;
import com.girhub.deripas.ai.example.model.Role;
import com.girhub.deripas.ai.example.repo.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatDialogControllerSyncIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatRepository chatRepository;

    private Long chatId;

    @BeforeEach
    void setUp() {
        chatRepository.deleteAll();
        chatId = chatRepository.save(Chat.builder().title("Диалог").build()).getId();
    }

    @Test
    void syncEntrySavesPromptAndModelAnswer() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(answer("Я Borisov GPT"));

        mockMvc.perform(post("/chat/{id}/entry", chatId).param("prompt", "Привет! Ты кто?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat/" + chatId));

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());

        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .isEqualTo("Привет! Ты кто?");
        assertThat(historyOf(chatId))
                .extracting(ChatEntry::getRole, ChatEntry::getContent)
                .containsExactly(
                        tuple(Role.USER, "Привет! Ты кто?"),
                        tuple(Role.ASSISTANT, "Я Borisov GPT")
                );
    }

    @Test
    void syncEntryRollsBackWhenModelFails() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("Ollama недоступна"));

        assertThatThrownBy(() -> mockMvc.perform(post("/chat/{id}/entry", chatId).param("prompt", "Привет")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(historyOf(chatId))
                .isEmpty();
    }

    private List<ChatEntry> historyOf(Long chatId) {
        return chatRepository.findById(chatId).orElseThrow()
                .getHistory()
                .stream()
                .sorted(Comparator.comparing(ChatEntry::getId))
                .toList();
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
