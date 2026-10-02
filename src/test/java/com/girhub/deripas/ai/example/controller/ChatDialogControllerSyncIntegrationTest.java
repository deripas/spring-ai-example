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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                .startsWith("Привет! Ты кто?");
        assertThat(historyOf(chatId))
                .satisfiesExactly(
                        entry -> assertChatEntry(entry, Role.USER, "Привет! Ты кто?"),
                        entry -> assertChatEntry(entry, Role.ASSISTANT, "Я Borisov GPT")
                );
    }

    @Test
    void secondEntrySendsPreviousDialogToModelInChronologicalOrder() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(answer("Я Borisov GPT"))
                .thenReturn(answer("Тебя зовут Антон"));

        mockMvc.perform(post("/chat/{id}/entry", chatId).param("prompt", "Привет! Меня зовут Антон"));
        mockMvc.perform(post("/chat/{id}/entry", chatId).param("prompt", "Как меня зовут?"));

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2))
                .call(promptCaptor.capture());

        assertThat(promptCaptor.getAllValues().getLast().getInstructions())
                .satisfiesExactly(
                        m -> assertMessage(m, MessageType.USER, "Привет! Меня зовут Антон"),
                        m -> assertMessage(m, MessageType.ASSISTANT, "Я Borisov GPT"),
                        m -> assertMessage(m, MessageType.USER, "Как меня зовут?")
                );
    }

    @Test
    void syncEntryForMissingChatReturns404WithoutCallingModel() throws Exception {
        mockMvc.perform(post("/chat/{id}/entry", Long.MAX_VALUE).param("prompt", "Привет"))
                .andExpect(status().isNotFound());

        verify(chatModel, never())
                .call(any(Prompt.class));
    }

    private List<ChatEntry> historyOf(Long chatId) {
        return chatRepository.findWithHistoryById(chatId).orElseThrow()
                .getHistory()
                .stream()
                .sorted(Comparator.comparing(ChatEntry::getId))
                .toList();
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static void assertMessage(Message message, MessageType messageType, String prefix) {
        assertThat(message.getMessageType())
                .isEqualTo(messageType);
        assertThat(message.getText())
                .startsWith(prefix);
    }

    private static void assertChatEntry(ChatEntry entry, Role role, String prefix) {
        assertThat(entry.getRole())
                .isEqualTo(role);
        assertThat(entry.getContent())
                .startsWith(prefix);
    }
}
