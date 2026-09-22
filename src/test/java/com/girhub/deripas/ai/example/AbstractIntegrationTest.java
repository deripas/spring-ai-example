package com.girhub.deripas.ai.example;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;

/**
 * Поднимает полный контекст с Postgres (pgvector) в Testcontainers.
 * Всё, что ходит в Ollama, замокано.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @MockitoBean
    protected ChatModel chatModel;

    @MockitoBean
    protected EmbeddingModel embeddingModel;

    @MockitoBean
    protected VectorStore vectorStore;

    @BeforeEach
    void stubChatModelOptions() {
        // ChatClient строит опции запроса через chatModel.getOptions().mutate()
        when(chatModel.getOptions())
                .thenReturn(ChatOptions.builder().build());
    }
}
