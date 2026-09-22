package com.girhub.deripas.ai.example.controller;

import com.girhub.deripas.ai.example.AbstractIntegrationTest;
import com.girhub.deripas.ai.example.model.Chat;
import com.girhub.deripas.ai.example.model.ChatEntry;
import com.girhub.deripas.ai.example.model.Role;
import com.girhub.deripas.ai.example.repo.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ChatAdminControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatRepository chatRepository;

    @BeforeEach
    void cleanDb() {
        chatRepository.deleteAll();
    }

    @Test
    void mainPageShowsAllChats() throws Exception {
        chatRepository.save(Chat.builder().title("Первый").build());
        chatRepository.save(Chat.builder().title("Второй").build());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"))
                .andExpect(model().attribute("chats", hasSize(2)))
                .andExpect(model().attributeDoesNotExist("chat"))
                .andExpect(content().string(containsString("Первый")))
                .andExpect(content().string(containsString("Второй")));
    }

    @Test
    void newChatIsPersistedAndRedirects() throws Exception {
        mockMvc.perform(post("/chat/new").param("title", "Новый чат"))
                .andExpect(status().is3xxRedirection());

        final List<Chat> chats = chatRepository.findAll();
        assertThat(chats).singleElement().satisfies(chat -> {
            assertThat(chat.getTitle()).isEqualTo("Новый чат");
            assertThat(chat.getCreatedAt()).isNotNull();
        });

        mockMvc.perform(post("/chat/new").param("title", "Ещё один"))
                .andExpect(redirectedUrl("/chat/" + chatRepository.findAll().stream()
                        .filter(c -> c.getTitle().equals("Ещё один"))
                        .findFirst()
                        .orElseThrow()
                        .getId()
                ));
    }

    @Test
    void showChatRendersHistory() throws Exception {
        final Chat chat = chatRepository.save(Chat.builder().title("С историей").build());
        chat.addChatEntry(ChatEntry.builder().role(Role.USER).content("Привет").build());
        chat.addChatEntry(ChatEntry.builder().role(Role.ASSISTANT).content("Здравствуй").build());
        final Long chatId = chatRepository.save(chat).getId();

        mockMvc.perform(get("/chat/{id}", chatId))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"))
                .andExpect(model().attribute("chat", hasProperty("id", is(chatId))))
                .andExpect(content().string(containsString("Привет")))
                .andExpect(content().string(containsString("Здравствуй")));
    }

    @Test
    void showMissingChatReturns404() throws Exception {
        mockMvc.perform(get("/chat/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteChatRemovesChatWithHistory() throws Exception {
        final Chat chat = chatRepository.save(Chat.builder().title("Удаляемый").build());
        chat.addChatEntry(ChatEntry.builder().role(Role.USER).content("Вопрос").build());
        final Long chatId = chatRepository.save(chat).getId();

        mockMvc.perform(post("/chat/{id}/delete", chatId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(chatRepository.findById(chatId)).isEmpty();
    }
}
