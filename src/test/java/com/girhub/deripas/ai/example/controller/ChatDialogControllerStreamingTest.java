package com.girhub.deripas.ai.example.controller;

import com.girhub.deripas.ai.example.services.ChatNotFoundException;
import com.girhub.deripas.ai.example.services.DialogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет только веб-слой: DialogService замокан, контекст без БД и Ollama.
 */
@WebMvcTest(ChatDialogController.class)
class ChatDialogControllerStreamingTest {

    private static final long CHAT_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DialogService dialogService;

    @Test
    void streamSendsEachTokenAsSseEvent() throws Exception {
        when(dialogService.proceedInteractionStreaming(CHAT_ID, "Ты кто?"))
                .thenReturn(Flux.just("Я ", "Borisov ", "GPT"));

        final MvcResult asyncResult = mockMvc.perform(get("/chat-stream/{id}", CHAT_ID)
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
                .satisfiesExactly(
                        line -> assertThat(line).contains("\"text\":\"Я \""),
                        line -> assertThat(line).contains("\"text\":\"Borisov \""),
                        line -> assertThat(line).contains("\"text\":\"GPT\""));
    }

    @Test
    void streamForMissingChatReturns404() throws Exception {
        when(dialogService.proceedInteractionStreaming(CHAT_ID, "Привет"))
                .thenReturn(Flux.error(new ChatNotFoundException(CHAT_ID)));

        final MvcResult asyncResult = mockMvc.perform(get("/chat-stream/{id}", CHAT_ID)
                        .param("prompt", "Привет")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound());
    }
}
