package com.girhub.deripas.ai.example.controller;

import com.girhub.deripas.ai.example.services.ChatNotFoundException;
import com.girhub.deripas.ai.example.services.DialogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет только веб-слой: DialogService замокан, контекст без БД и Ollama.
 */
@WebMvcTest(ChatDialogController.class)
class ChatDialogControllerSyncTest {

    private static final long CHAT_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DialogService dialogService;

    @Test
    void syncEntryPassesPromptToDialogAndRedirectsToChat() throws Exception {
        when(dialogService.proceedInteractionSync(CHAT_ID, "Привет! Ты кто?"))
                .thenReturn("Я Borisov GPT");

        mockMvc.perform(post("/chat/{id}/entry", CHAT_ID).param("prompt", "Привет! Ты кто?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat/" + CHAT_ID));

        verify(dialogService).proceedInteractionSync(CHAT_ID, "Привет! Ты кто?");
    }

    @Test
    void syncEntryForMissingChatReturns404() throws Exception {
        when(dialogService.proceedInteractionSync(CHAT_ID, "Привет"))
                .thenThrow(new ChatNotFoundException(CHAT_ID));

        mockMvc.perform(post("/chat/{id}/entry", CHAT_ID).param("prompt", "Привет"))
                .andExpect(status().isNotFound());
    }
}
