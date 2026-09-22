package com.girhub.deripas.ai.example.controller;

import com.girhub.deripas.ai.example.AbstractIntegrationTest;
import com.girhub.deripas.ai.example.model.Chat;
import com.girhub.deripas.ai.example.model.ChatEntry;
import com.girhub.deripas.ai.example.model.Role;
import com.girhub.deripas.ai.example.repo.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatDialogControllerStreamingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatRepository chatRepository;

    private Long chatId;

    @BeforeEach
    void setUp() {
        chatRepository.deleteAll();
        chatId = chatRepository.save(Chat.builder().title("Стрим").build()).getId();
    }

    @Test
    void streamSendsTokensAndSavesDialog() throws Exception {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        token("Я "),
                        token("Borisov "),
                        token("GPT")
                ));

        final MvcResult asyncResult = mockMvc.perform(get("/chat-stream/{id}", chatId)
                        .param("prompt", "Ты кто?")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        final String body = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // chat.js читает data.text из каждого события
        assertThat(body.lines().filter(line -> line.startsWith("data:")).toList())
                .hasSize(3)
                .satisfiesExactly(
                        line -> assertThat(line).contains("\"text\":\"Я \""),
                        line -> assertThat(line).contains("\"text\":\"Borisov \""),
                        line -> assertThat(line).contains("\"text\":\"GPT\""));

        assertThat(historyOf(chatId))
                .extracting(ChatEntry::getRole, ChatEntry::getContent)
                .containsExactly(
                        tuple(Role.USER, "Ты кто?"),
                        tuple(Role.ASSISTANT, "Я Borisov GPT")
                );
    }

    private List<ChatEntry> historyOf(Long chatId) {
        return chatRepository.findById(chatId).orElseThrow().getHistory().stream()
                .sorted(Comparator.comparing(ChatEntry::getId))
                .toList();
    }

    private static ChatResponse token(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
